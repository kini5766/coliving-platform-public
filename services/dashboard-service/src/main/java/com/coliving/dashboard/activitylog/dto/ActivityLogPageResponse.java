package com.coliving.dashboard.activitylog.dto;

import java.util.List;

/** 활동 로그 "더보기" 팝업에 내려주는 한 페이지 분량의 응답. */
public record ActivityLogPageResponse(
        List<ActivityLogResponse> items,    // 이번 페이지의 활동 로그 (최근 변경 순)
        boolean hasNext                     // 다음 페이지가 더 있는지
) {
}