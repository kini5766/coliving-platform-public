package com.coliving.dashboard.kafka.consumer;

import com.coliving.dashboard.activitylog.service.ActivityLogService;
import com.coliving.dashboard.kafka.dto.PaymentCompletedEvent;
import com.coliving.dashboard.snapshot.service.DashboardSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 결제 완료 이벤트를 받아 당월 수납 집계에 더하고, 활동 로그에 남긴다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConsumer {

    private final JsonMapper jsonMapper;
    private final ActivityLogService activityLogService;
    private final DashboardSnapshotService snapshotService;

    /**
     * JSON 문자열로 받아 이벤트 객체로 변환한다.
     * - 변환에 실패했거나 결제 ID가 없는 메시지는 다시 받아도 소용없으므로 로그만 남기고 버린다.
     * - 집계·저장에 실패하면 예외를 던져서 Kafka 재시도에 맡긴다. (삼키면 금액이 영구히 빠진다)
     *
     * 두 단계는 같은 이벤트를 다시 받아도 결과가 같다.
     * - 집계: 반영한 결제 키를 스냅샷에 함께 저장하므로, 이미 반영한 결제는 건너뛴다.
     * - 로그: 결제 ID로 저장하고 이벤트 시간을 버전으로 쓰므로, 같은 이벤트는 무시된다.
     * 그래서 어느 단계에서 실패해 재시도해도 금액이 빠지거나 두 번 더해지지 않는다.
     */
    @KafkaListener(topics = "payment.completed")
    public void consume(String message) {
        PaymentCompletedEvent event;
        try {
            event = jsonMapper.readValue(message, PaymentCompletedEvent.class);
        } catch (Exception e) {
            log.error("결제 완료 이벤트 변환 실패(재시도 없이 버림): {}", message, e);
            return;
        }
        if (event.paymentId() == null) {
            log.error("결제 ID가 없는 결제 완료 이벤트(재시도 없이 버림): {}", message);
            return;
        }

        snapshotService.addPayment(event.paymentId(), event.buildingId(), event.buildingName(),
                event.amount(), event.deposit());
        activityLogService.savePaymentActivity(event);
    }
}