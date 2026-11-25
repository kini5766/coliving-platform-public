package com.coliving.platform.contract.service;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.entity.Payment;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

/**
 * 저장된 계약서 본문 → 화면·PDF에 보여줄 본문.
 *
 * 계약일({{contractDate}}) = 결제 완료일. 저장본에는 빈칸으로 남아 있으므로 보여줄 때 채운다.
 *   - 결제가 완료된 적 있는 신청(PAID 이후 상태) → 결제 승인일(한국 날짜)
 *   - 그 전(심사 중·결제 대기 등) → 빈칸
 * 저장본(contract_document.content)과 해시는 그대로다. 서명할 때 비교하는 해시도 저장본 기준.
 *
 * @author 한성현
 */
@Component
public class ContractContentView {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 결제가 완료된 뒤의 상태들 (입주 전 취소·환불돼도 계약일은 결제일로 남긴다)
    private static final Set<ContractStatus> PAID_OR_LATER = EnumSet.of(
            ContractStatus.PAID, ContractStatus.COMPLETED,
            ContractStatus.MOVE_IN_CANCEL_REQUESTED, ContractStatus.REFUNDED,
            ContractStatus.TERMINATION_REQUESTED, ContractStatus.TERMINATED);

    private final PaymentRepository paymentRepository;

    public ContractContentView(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /** 보여줄 본문. 계약서가 없으면 null */
    public String content(Contract c, ContractDocument d) {
        if (d == null) return null;
        return ContractContentRenderer.withContractDate(d.getContent(), contractDate(c));
    }

    /** 계약일 = 결제 완료일. 아직 결제 전이면 null */
    public LocalDate contractDate(Contract c) {
        if (!PAID_OR_LATER.contains(c.getStatus())) return null;
        return paymentRepository.findFirstByApplicationIdAndApprovedAtIsNotNullOrderByApprovedAtAsc(c.getId())
                .map(Payment::getApprovedAt)
                .map(at -> LocalDate.ofInstant(at, SEOUL))
                .orElse(null);
    }
}
