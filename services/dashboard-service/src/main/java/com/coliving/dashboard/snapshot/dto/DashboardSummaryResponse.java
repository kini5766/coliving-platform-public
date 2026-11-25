package com.coliving.dashboard.snapshot.dto;

import java.util.List;

/** 대시보드 화면의 지표 카드, 재무 요약, 6개월 추이를 한 번에 내려주는 응답. */
public record DashboardSummaryResponse(
        Long buildingId,           // 건물 ID
        String buildingName,       // 건물명
        String month,              // 조회 월 (yyyy-MM)
        Cards cards,               // 위쪽 지표 카드
        Finance finance,           // 아래쪽 재무 카드
        List<TrendPoint> trend     // 최근 6개월 추이
) {

    /** 위쪽 지표 카드 3개 (입주율, 계약 승인 대기, 입주·퇴거 예정). */
    public record Cards(
            Double occupancyRate,            // 입주율
            Double occupancyRateDiff,        // 입주율 지난달 대비 증감
            Integer pendingApprovalCount,    // 계약 승인 대기 신청 수
            Integer moveInScheduledCount,    // 입주 예정 호실 수 (옛 스냅샷은 null)
            Integer moveOutScheduledCount    // 퇴거 예정 호실 수
    ) {}

    /** 아래쪽 재무 카드 4개 (수납액, 환불, 실 수납액, 보증금 보관액). 수납·환불·실 수납은 이용료 기준이다. */
    public record Finance(
            Long monthCollectedAmount,     // 이번 달 수납액 (보증금 제외)
            Double collectedChangeRate,    // 수납액 지난달 대비 증감률
            Long monthRefundedAmount,      // 이번 달 환불 완료 금액 (보증금 제외)
            Integer monthRefundedCount,    // 이번 달 환불 완료 건수
            Long netCollectedAmount,       // 실 수납액 (수납액 - 환불)
            Long depositHeldAmount         // 보증금 보관액
    ) {}

    /** 6개월 추이 그래프의 점 하나. */
    public record TrendPoint(
            String month,                  // 월 (yyyy-MM)
            Double occupancyRate,          // 입주율
            Long collectedAmount,          // 수납액
            Long refundedAmount            // 환불액
    ) {}
}