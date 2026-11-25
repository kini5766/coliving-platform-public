package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.common.exception.ContractFileException;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.entity.Payment;
import com.coliving.platform.contract.entity.PaymentReceipt;
import com.coliving.platform.contract.enums.PaymentStatus;
import com.coliving.platform.contract.enums.ReceiptType;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.PaymentReceiptRepository;
import com.coliving.platform.contract.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 결제 영수증(PAYMENT) / 취소 영수증(CANCEL) PDF 발급·조회.
 *
 *   받을 때 처음이면 PDF를 만들어 저장하고(payment_receipt 1행), 이후에는 저장본을 그대로 내려준다.
 *   (payment_id, receipt_type) UNIQUE라서 같은 영수증은 한 번만 만들어지고, 동시에 눌러도 한쪽 파일은 지운다.
 *
 *   PAYMENT : 승인된 결제가 있을 때 (이후 환불돼도 결제 영수증은 그대로 받을 수 있다)
 *   CANCEL  : 결제가 REFUNDED일 때만
 *
 * @author 한성현
 */
@Service
public class PaymentReceiptService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceiptService.class);

    private final ContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReceiptRepository receiptRepository;
    private final ReceiptPdfRenderer pdfRenderer;
    private final ContractFileStorage storage;

    public PaymentReceiptService(ContractRepository contractRepository,
                                 PaymentRepository paymentRepository,
                                 PaymentReceiptRepository receiptRepository,
                                 ReceiptPdfRenderer pdfRenderer,
                                 ContractFileStorage storage) {
        this.contractRepository = contractRepository;
        this.paymentRepository = paymentRepository;
        this.receiptRepository = receiptRepository;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
    }

    /** 영수증 PDF. 처음이면 발급하고, 저장할 때의 해시와 다르면 내려주지 않는다 */
    public byte[] load(Long userId, String applicationNo, ReceiptType type) {
        Contract c = contractRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        if (!c.isOwnedBy(userId)) {
            throw new ApplicationAccessDeniedException("본인의 영수증만 받을 수 있습니다.");
        }
        Payment p = findPayment(c, type);

        PaymentReceipt receipt = receiptRepository.findByPaymentIdAndReceiptType(p.getId(), type)
                .orElseGet(() -> issue(c, p, type));

        byte[] pdf = storage.read(receipt.getFilePath());
        if (!ContractDocument.sha256Hex(pdf).equals(receipt.getContentHash())) {
            throw new ContractFileException("영수증 PDF 해시 불일치: " + receipt.getFilePath(), null);
        }
        return pdf;
    }

    // 영수증을 받을 수 있는 결제. 조건에 안 맞으면 404(결제 없음) / 409(취소 영수증인데 환불 전)
    private Payment findPayment(Contract c, ReceiptType type) {
        if (type == ReceiptType.CANCEL) {
            return paymentRepository
                    .findFirstByApplicationIdAndStatusAndApprovedAtIsNotNullOrderByApprovedAtAsc(
                            c.getId(), PaymentStatus.REFUNDED)
                    .orElseThrow(() -> new IllegalStateException("환불이 완료된 결제만 취소 영수증을 받을 수 있습니다."));
        }
        return paymentRepository.findFirstByApplicationIdAndApprovedAtIsNotNullOrderByApprovedAtAsc(c.getId())
                .orElseThrow(() -> new ApplicationNotFoundException("결제가 완료된 내역이 없습니다."));
    }

    // PDF 생성 → 파일 저장 → payment_receipt 저장. 파일 경로에 UUID가 있어 서로 겹치지 않는다
    private PaymentReceipt issue(Contract c, Payment p, ReceiptType type) {
        long total = p.getAmount();
        long deposit = c.getDeposit();
        if (total < deposit) {
            throw new IllegalStateException("결제 금액이 보증금보다 작아 영수증을 만들 수 없습니다.");
        }
        long firstMonthFee = total - deposit;       // 총 결제금액(첫 결제금액) - 보증금
        Instant canceledAt = type == ReceiptType.CANCEL
                ? (p.getCanceledAt() != null ? p.getCanceledAt() : p.getUpdatedAt())
                : null;

        Instant issuedAt = Instant.now();
        String dataHash = dataHash(type, c.getApplicationNo(), p, canceledAt, total, deposit, firstMonthFee);
        byte[] pdf = pdfRenderer.render(new ReceiptPdfRenderer.Input(
                type, c.getApplicationNo(), p.getOrderId(), p.getTid(), p.getPaymentMethod(),
                p.getApprovedAt(), canceledAt, total, deposit, firstMonthFee, issuedAt, dataHash));

        String path = "receipts/" + c.getApplicationNo() + "/"
                + type.name().toLowerCase() + "-" + UUID.randomUUID() + ".pdf";
        try {
            storage.save(path, pdf);
            return receiptRepository.saveAndFlush(
                    PaymentReceipt.issue(p.getId(), type, path, ContractDocument.sha256Hex(pdf)));
        } catch (DataIntegrityViolationException e) {
            deleteQuietly(path);
            // 동시에 눌러서 다른 요청이 먼저 발급한 경우: 그 영수증을 쓴다
            return receiptRepository.findByPaymentIdAndReceiptType(p.getId(), type).orElseThrow(() -> e);
        } catch (RuntimeException e) {
            deleteQuietly(path);
            throw e;
        }
    }

    /** 영수증에 적힌 내용(주문·거래·수단·승인/취소 시각·금액)의 SHA-256 → PDF 하단에 찍는다 */
    static String dataHash(ReceiptType type, String applicationNo, Payment p, Instant canceledAt,
                           long total, long deposit, long firstMonthFee) {
        return ContractDocument.sha256Hex(String.join("|",
                type.name(), applicationNo, p.getOrderId(), String.valueOf(p.getTid()),
                String.valueOf(p.getPaymentMethod()), String.valueOf(p.getApprovedAt()),
                String.valueOf(canceledAt), String.valueOf(total), String.valueOf(deposit),
                String.valueOf(firstMonthFee)));
    }

    private void deleteQuietly(String path) {
        try {
            storage.delete(path);
        } catch (RuntimeException e) {
            log.warn("영수증 파일 정리 실패: {}", path, e);
        }
    }
}
