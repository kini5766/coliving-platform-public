package com.coliving.dashboard.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/** 계약 서비스가 보내는 "환불 완료" 이벤트. 활동 로그와 당월 환불 집계에 쓴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRefundedEvent(
        String eventId,             // 이벤트 고유 ID
        OffsetDateTime occurredAt,  // 환불 완료 시간 (payment.canceled_at, KST)
        Long paymentId,             // 결제 ID
        Long applicationId,         // 계약 ID
        String applicantName,       // 계약자 이름
        Long buildingId,            // 건물 ID
        String buildingName,        // 건물명
        String roomName,            // 호실명
        Long refundAmount,          // 환불 금액 (입주 전 취소는 전액)
        Long deposit                // 환불 금액에 포함된 보증금 (옛 메시지에는 없어서 null일 수 있다)
) {}