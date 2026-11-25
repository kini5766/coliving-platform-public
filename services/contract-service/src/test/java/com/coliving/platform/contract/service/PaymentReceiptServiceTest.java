package com.coliving.platform.contract.service;

import com.coliving.platform.common.config.ReceiptBusinessProperties;
import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.common.exception.ContractFileException;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.Payment;
import com.coliving.platform.contract.entity.PaymentReceipt;
import com.coliving.platform.contract.enums.PaymentStatus;
import com.coliving.platform.contract.enums.ReceiptType;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.PaymentReceiptRepository;
import com.coliving.platform.contract.repository.PaymentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReceiptServiceTest {

    private static final String NO = "CT-20261102-0001";
    private static final long DEPOSIT = 1_000_000L;
    private static final long TOTAL = 1_780_000L;

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentReceiptRepository receipts = mock(PaymentReceiptRepository.class);
    private final Map<String, byte[]> files = new HashMap<>();
    private final ContractFileStorage storage = new ContractFileStorage() {
        public void save(String path, byte[] content) { files.put(path, content); }
        public byte[] read(String path) { return files.get(path); }
        public void delete(String path) { files.remove(path); }
    };

    private Payment payment;
    private PaymentReceiptService service;

    @BeforeEach
    void setUp() {
        service = new PaymentReceiptService(contracts, payments, receipts,
                new ReceiptPdfRenderer(new ReceiptBusinessProperties("코리빙", "123-45-67890", "홍길동", "서울")), storage);

        Contract c = mock(Contract.class);
        when(c.getId()).thenReturn(10L);
        when(c.getApplicationNo()).thenReturn(NO);
        when(c.getDeposit()).thenReturn(DEPOSIT);
        when(c.isOwnedBy(1L)).thenReturn(true);
        when(contracts.findByApplicationNo(NO)).thenReturn(Optional.of(c));

        payment = Payment.start(10L, NO + "-abcd1234", TOTAL);
        ReflectionTestUtils.setField(payment, "id", 20L);
        payment.saveTid("T1");
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
        ReflectionTestUtils.setField(payment, "paymentMethod", "MONEY");
        ReflectionTestUtils.setField(payment, "approvedAt", Instant.parse("2026-11-02T05:05:00Z"));
        when(payments.findFirstByApplicationIdAndApprovedAtIsNotNullOrderByApprovedAtAsc(10L))
                .thenReturn(Optional.of(payment));
        when(receipts.saveAndFlush(any(PaymentReceipt.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void 처음_받으면_발급해서_저장하고_보증금과_첫달_이용료로_나눈다() throws IOException {
        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.PAYMENT)).thenReturn(Optional.empty());

        byte[] pdf = service.load(1L, NO, ReceiptType.PAYMENT);

        String text = text(pdf);
        assertThat(text).contains("결제 영수증", "KAKAOPAY / MONEY", "1,000,000원", "780,000원", "1,780,000원");
        assertThat(files).hasSize(1);
        verify(receipts).saveAndFlush(any(PaymentReceipt.class));
    }

    @Test
    void 이미_발급된_영수증은_저장본을_그대로_내려준다() {
        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.PAYMENT)).thenReturn(Optional.empty());
        byte[] first = service.load(1L, NO, ReceiptType.PAYMENT);
        PaymentReceipt saved = receiptSaved();
        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.PAYMENT)).thenReturn(Optional.of(saved));

        byte[] second = service.load(1L, NO, ReceiptType.PAYMENT);

        assertThat(second).isEqualTo(first);
        verify(receipts, times(1)).saveAndFlush(any());      // 다시 발급하지 않는다
        assertThat(files).hasSize(1);
    }

    @Test
    void 저장된_파일이_바뀌었으면_내려주지_않는다() {
        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.PAYMENT)).thenReturn(Optional.empty());
        service.load(1L, NO, ReceiptType.PAYMENT);
        PaymentReceipt saved = receiptSaved();
        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.PAYMENT)).thenReturn(Optional.of(saved));
        files.replaceAll((k, v) -> "변조".getBytes());

        assertThatThrownBy(() -> service.load(1L, NO, ReceiptType.PAYMENT)).isInstanceOf(ContractFileException.class);
    }

    @Test
    void 결제가_REFUNDED가_아니면_취소_영수증을_발급하지_않는다() {
        when(payments.findFirstByApplicationIdAndStatusAndApprovedAtIsNotNullOrderByApprovedAtAsc(10L, PaymentStatus.REFUNDED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.load(1L, NO, ReceiptType.CANCEL))
                .isInstanceOf(IllegalStateException.class);
        verify(receipts, never()).saveAndFlush(any());
        assertThat(files).isEmpty();
    }

    @Test
    void 환불된_결제는_취소_영수증을_발급한다() throws IOException {
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.REFUNDED);
        ReflectionTestUtils.setField(payment, "canceledAt", Instant.parse("2026-11-03T01:00:00Z"));
        when(payments.findFirstByApplicationIdAndStatusAndApprovedAtIsNotNullOrderByApprovedAtAsc(10L, PaymentStatus.REFUNDED))
                .thenReturn(Optional.of(payment));
        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.CANCEL)).thenReturn(Optional.empty());

        String text = text(service.load(1L, NO, ReceiptType.CANCEL));

        assertThat(text).contains("결제 취소 영수증", "2026-11-03 10:00:00 KST", "취소(환불) 금액");
    }

    @Test
    void 동시에_발급돼_유니크_위반이면_먼저_만들어진_영수증을_쓰고_내_파일은_지운다() {
        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.PAYMENT)).thenReturn(Optional.empty());
        byte[] existing = service.load(1L, NO, ReceiptType.PAYMENT);
        PaymentReceipt winner = receiptSaved();
        files.clear();
        files.put(winner.getFilePath(), existing);

        when(receipts.findByPaymentIdAndReceiptType(20L, ReceiptType.PAYMENT))
                .thenReturn(Optional.empty())               // 처음 확인할 때는 아직 없음
                .thenReturn(Optional.of(winner));           // 저장 실패 뒤에는 있음
        when(receipts.saveAndFlush(any(PaymentReceipt.class))).thenThrow(new DataIntegrityViolationException("dup"));

        byte[] pdf = service.load(1L, NO, ReceiptType.PAYMENT);

        assertThat(pdf).isEqualTo(existing);
        assertThat(files).containsOnlyKeys(winner.getFilePath());     // 내가 만든 파일은 정리됨
    }

    @Test
    void 남의_영수증과_결제_없는_신청은_거절한다() {
        assertThatThrownBy(() -> service.load(2L, NO, ReceiptType.PAYMENT))
                .isInstanceOf(ApplicationAccessDeniedException.class);

        when(payments.findFirstByApplicationIdAndApprovedAtIsNotNullOrderByApprovedAtAsc(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.load(1L, NO, ReceiptType.PAYMENT))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    // 직전 발급에서 저장된 영수증 행을 만든다 (파일 경로·해시는 실제 저장본에서)
    private PaymentReceipt receiptSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentReceipt.class);
        verify(receipts, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
