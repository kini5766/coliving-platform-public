package com.coliving.reservation.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * 예약 상태 변경 이벤트를 Kafka(reservation.status-changed)로 보낸다.
 *
 * - 예약 저장이 커밋된 뒤에만 보낸다. (롤백된 예약의 이벤트가 나가지 않게)
 * - Kafka 전송이 실패해도 예약 API 는 실패시키지 않고 로그만 남긴다.
 *   (Outbox 를 쓰지 않으므로 브로커가 내려가 있는 동안의 이벤트는 유실된다)
 */
@Slf4j
@Component
public class ReservationStatusEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final String topic;

    public ReservationStatusEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${app.kafka.topics.reservation-status-changed:reservation.status-changed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ReservationStatusChangedEvent event) {
        try {
            String payload = jsonMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, String.valueOf(event.reservationId()), payload)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("예약 상태 이벤트 전송 실패: reservationId={}, status={}",
                                    event.reservationId(), event.reservationStatus(), error);
                        }
                    });
        } catch (Exception e) {
            log.error("예약 상태 이벤트 발행 실패: reservationId={}, status={}",
                    event.reservationId(), event.reservationStatus(), e);
        }
    }
}
