package com.coliving.platform.faq.repository;

/** 7-8 요약 (기간·건물 기준) */
public record FaqQuestionLogSummaryRow(Long totalCount, Long unansweredCount, Long cachedCount, Double avgLatencyMs) {
}
