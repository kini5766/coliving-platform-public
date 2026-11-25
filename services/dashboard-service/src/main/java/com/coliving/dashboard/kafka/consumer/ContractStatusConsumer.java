package com.coliving.dashboard.kafka.consumer;

import com.coliving.dashboard.activitylog.service.ActivityLogService;
import com.coliving.dashboard.kafka.dto.ContractStatusChangedEvent;
import com.coliving.dashboard.roomstatus.service.RoomStatusService;
import com.coliving.dashboard.snapshot.service.DashboardSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 계약 상태 변경 이벤트를 받아 호실 현황과 활동 로그에 나눠 전달한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractStatusConsumer {

    private final JsonMapper jsonMapper;
    private final RoomStatusService roomStatusService;
    private final ActivityLogService activityLogService;
    private final DashboardSnapshotService snapshotService;

    /**
     * JSON 문자열로 받아 이벤트 객체로 변환한다.
     * - 변환에 실패한 메시지는 다시 받아도 소용없으므로 로그만 남기고 버린다.
     * - 처리에 실패하면 예외를 던져서 Kafka 재시도에 맡긴다. (삼키면 호실 현황과 활동 로그가 서로 어긋난 채 남는다)
     *
     * 세 단계는 같은 이벤트를 다시 받아도 결과가 같다.
     * - 호실 현황: 같은 시각의 이벤트는 같은 값으로 다시 저장한다.
     * - 활동 로그: 같은 ID와 같은 버전이면 무시된다.
     * - 스냅샷 갱신: 금액을 더하지 않고 현재 상태로 다시 계산한다.
     * 그래서 중간에 실패해 처음부터 다시 처리해도 문제가 없다.
     */
    @KafkaListener(topics = "contract.status-changed")
    public void consume(String message) {
        ContractStatusChangedEvent event;
        try {
            event = jsonMapper.readValue(message, ContractStatusChangedEvent.class);
        } catch (Exception e) {
            log.error("계약 상태 이벤트 변환 실패(재시도 없이 버림): {}", message, e);
            return;
        }

        roomStatusService.applyContractEvent(event);       // 호실 현황 갱신
        activityLogService.saveContractActivity(event);    // 활동 로그 저장
        snapshotService.refreshToday(event.buildingId(), event.buildingName());
    }
}