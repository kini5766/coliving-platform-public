package com.coliving.platform.contract.dto.application;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.enums.ContractStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * [접수 완료 / 나의 신청 상세 / 계약 완료] GET /contract-service/apply/applications/{applicationNo}
 *
 * 화면 3곳이 같은 신청서 1건을 보여주므로 응답 하나로 쓴다.
 * 상태에 따라 값이 없는 필드는 null. 호실·금액은 신청 시점 스냅샷이다.
 * 본인 신청서인지는 JWT의 회원과 신청서의 회원을 비교해서 확인한다 (아니면 403).
 *
 * @param status             JSON에는 "SUBMITTED"처럼 enum 이름 문자열로 나간다
 * @param roomId             반려 후 "다시 신청하기" → 1-1 화면으로 이동할 때 사용
 * @param moveOutDate        퇴실 예정일 (입주일 + 개월 - 1일)
 * @param firstPaymentAmount 첫 결제 금액 (monthlyFee + deposit)
 * @param submittedAt        제출 시각 (= 금액 고정 시각). DRAFT면 null
 * @param rejectReason       REJECTED일 때만
 * @param paymentDeadline    결제 기한. APPROVED일 때만 (승인 + 1일)
 * @param payment            결제 완료 건이 있을 때만, 없으면 null
 * @author 한성현
 */
public record ApplicationDetailResponse(
        String applicationNo,
        ContractStatus status,
        Long roomId,

        // 호실
        String buildingName,
        String roomName,
        String roomType,

        // 기간
        LocalDate moveInDate,
        LocalDate moveOutDate,
        Integer months,

        // 금액
        Long monthlyFee,
        Long deposit,
        Long firstPaymentAmount,

        OffsetDateTime submittedAt,
        String rejectReason,
        OffsetDateTime paymentDeadline,

        PaymentSummary payment
) {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    /**
     * 신청서 1건 → 상세 응답.
     * - submittedAt: 제출할 때 금액이 고정되므로 amountFixedAt을 제출 시각으로 쓴다 (DRAFT면 null)
     * - paymentDeadline: APPROVED일 때만 expiresAt(승인 + 1일)
     * - payment: 결제 완료 건이 있을 때만 (없으면 null)
     */
    public static ApplicationDetailResponse from(Contract c, PaymentSummary payment) {
        return new ApplicationDetailResponse(
                c.getApplicationNo(),
                c.getStatus(),
                c.getRoomId(),
                c.getBuildingName(),
                c.getRoomName(),
                c.getRoomType(),
                c.getMoveInDate(),
                c.getMoveOutDate(),
                c.getContractMonths() == null ? null : c.getContractMonths().intValue(),
                c.getMonthlyFee(),
                c.getDeposit(),
                c.getFirstPayment(),
                toKst(c.getAmountFixedAt()),
                c.getStatus() == ContractStatus.REJECTED ? c.getRejectReason() : null,
                c.getStatus() == ContractStatus.APPROVED ? toKst(c.getExpiresAt()) : null,
                payment
        );
    }

    /** 결제 정보가 필요 없는 응답용 */
    public static ApplicationDetailResponse from(Contract c) {
        return from(c, null);
    }

    private static OffsetDateTime toKst(Instant t) {
        return t == null ? null : t.atOffset(KST);
    }

    /**
     * [계약 완료] 화면의 결제 정보
     *
     * @param method     결제 수단 ("KAKAOPAY")
     * @param receiptUrl 항상 null. 영수증은 GET /contract-service/contracts/{applicationNo}/receipt 로 받는다
     */
    public record PaymentSummary(
            String method,
            Long paidAmount,
            OffsetDateTime paidAt,
            String receiptUrl
    ) {}
}
