package com.coliving.dashboard.kafka.consumer;

import com.coliving.dashboard.activitylog.service.ActivityLogService;
import com.coliving.dashboard.kafka.dto.ReservationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 예약 상태 변경 이벤트를 받아 활동 로그에 저장한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationConsumer {

    private final JsonMapper jsonMapper;
    private final ActivityLogService activityLogService;

    /**
     * JSON 문자열로 받아 이벤트 객체로 변환한다.
     * - 변환에 실패한 메시지는 다시 받아도 소용없으므로 로그만 남기고 버린다.
     * - 저장에 실패하면 예외를 던져서 Kafka 재시도에 맡긴다. 활동 로그는 같은 ID와 같은 버전이면 무시되므로 다시 받아도 안전하다.
     */
    @KafkaListener(topics = "reservation.status-changed")
    public void consume(String message) {
        ReservationStatusChangedEvent event;
        try {
            event = jsonMapper.readValue(message, ReservationStatusChangedEvent.class);
        } catch (Exception e) {
            log.error("예약 상태 이벤트 변환 실패(재시도 없이 버림): {}", message, e);
            return;
        }

        activityLogService.saveReservationActivity(event);
    }
}