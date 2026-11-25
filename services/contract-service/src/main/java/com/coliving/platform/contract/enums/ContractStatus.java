package com.coliving.platform.contract.enums;

/**
 * contract_application.status CHECK 값과 1:1. DB에는 이름 그대로(STRING) 저장된다.
 *
 * @author 한성현
 */
public enum ContractStatus {
    DRAFT,                      // 작성중 (기한 2일)
    SUBMITTED,                  // 심사중
    APPROVED,                   // 승인·결제대기 (기한 1일)
    PAID,                       // 결제완료
    COMPLETED,                  // 계약 완료 = 입주 (입주일이 되면 MoveInScheduler가 PAID → COMPLETED)
    REJECTED,                   // 반려 (최종, 재신청은 새 신청)
    CANCELED,                   // 심사 중 사용자 취소
    EXPIRED,                    // 기한 경과
    REFUNDED,                   // 입주 전 취소 → 환불 완료
    MOVE_IN_CANCEL_REQUESTED,   // 입주 전 취소 요청
    TERMINATION_REQUESTED,      // 퇴실 신청
    TERMINATED;                 // 계약 종료

    /**
     * 호실 기간을 차지하는 상태 = DB 제약 no_overlap_booking의 WHERE 목록과 반드시 같게 유지할 것.
     * 신청 화면의 예약 기간 표시, 매물 서비스에 주는 입주 가능 여부가 이 목록으로 판단한다.
     */
    public static final java.util.Set<ContractStatus> BOOKING = java.util.Collections.unmodifiableSet(
            java.util.EnumSet.of(DRAFT, SUBMITTED, APPROVED, PAID, COMPLETED,
                    MOVE_IN_CANCEL_REQUESTED, TERMINATION_REQUESTED, TERMINATED));
}
