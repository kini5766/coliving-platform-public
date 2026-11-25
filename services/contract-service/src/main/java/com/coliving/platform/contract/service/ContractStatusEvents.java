package com.coliving.platform.contract.service;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractStatusHistory;
import com.coliving.platform.contract.entity.OutboxEvent;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.mail.ContractMailData;
import com.coliving.platform.contract.mail.ContractMailEvent;
import com.coliving.platform.contract.mail.MailType;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.OutboxEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 신청 상태가 바뀔 때 다른 서비스에 알릴 이벤트를 아웃박스(outbox_event)에 쌓는다.
 * 상태 이력(ContractStatusHistory)을 저장한 바로 뒤, 같은 트랜잭션 안에서 부른다:
 *     statusEvents.publish(historyRepository.save(ContractStatusHistory.xxx(...)));
 * → 상태 변경이 롤백되면 이벤트도 같이 사라지고, 커밋되면 OutboxPublishScheduler가 Kafka로 보낸다.
 *
 * 보내는 이벤트
 *   1) contract.status-changed (대시보드 API 명세 3-1의 13개 필드, deposit 포함)
 *      상태가 바뀔 때마다 1건. 작성 중(DRAFT) 생성(null → DRAFT)과 DRAFT 만료(DRAFT → EXPIRED)도 보낸다.
 *      대시보드는 DRAFT를 받으면 호실 현황에 반영하지 않고 경고 로그만 남긴다(RoomStatusMapper).
 *      DRAFT 단계에서는 applicantName이 아직 null일 수 있다.
 *   2) contract.completed (커뮤니티: 입주 예정자 → 입주자 전환)
 *      PAID → COMPLETED (입주일이 됨, MoveInScheduler)일 때만. status-changed와 별도로 1건 더 쌓는다. ⚠ 커뮤니티 쪽 수신 코드는 아직 없음 — 필드는 커뮤니티 담당과 맞출 것
 *
 * 메일: 심사 승인·반려, 결제 완료면 ContractMailEvent도 던진다 (어떤 변경이 메일인지는 MailType.forTransition).
 *   ContractMailListener가 커밋된 뒤에 비동기로 보내므로, 롤백되면 메일도 나가지 않는다.
 *
 * 결제 금액 이벤트(payment.completed / payment.refunded)는 여기가 아니라 PaymentService가 쌓는다.
 * 대시보드 수납액 집계가 payment.*를 쓰므로 status-changed와 별개로 계속 보낸다.
 *
 * 소비자는 payload의 eventId로 중복을 거른다 (아웃박스는 최소 1번 전달).
 *
 * @author 한성현
 */
@Component
public class ContractStatusEvents {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter OCCURRED_AT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ContractRepository contractRepository;
    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ContractStatusEvents(ContractRepository contractRepository,
                                OutboxEventRepository outboxRepository,
                                JsonMapper jsonMapper,
                                ApplicationEventPublisher eventPublisher) {
        this.contractRepository = contractRepository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 방금 저장한 상태 이력으로 이벤트를 쌓는다 (모든 상태 변경 → status-changed 1건, PAID → COMPLETED면 completed 1건 추가) */
    public void publish(ContractStatusHistory h) {
        if (h == null) return;
        ContractStatus from = h.getFromStatus();
        ContractStatus to = h.getToStatus();

        // 벌크 UPDATE(clearAutomatically)로 바뀐 경우도 있어 DB에서 다시 읽는다 (같은 트랜잭션이라 바뀐 값이 보임)
        Contract c = contractRepository.findById(h.getApplicationId()).orElseThrow();
        Instant at = h.getCreatedAt() == null ? Instant.now() : h.getCreatedAt();
        String occurredAt = OCCURRED_AT.format(at.truncatedTo(ChronoUnit.SECONDS).atOffset(KST));

        UUID statusEventId = UUID.randomUUID();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("eventId", statusEventId.toString());
        p.put("occurredAt", occurredAt);                       // "2026-11-05T15:42:20+09:00"
        p.put("applicationId", c.getId());
        p.put("applicantName", c.getApplicantName());          // 제출 전이면 null
        p.put("buildingId", c.getBuildingId());
        p.put("buildingName", c.getBuildingName());
        p.put("roomId", c.getRoomId());
        p.put("roomName", c.getRoomName());
        p.put("previousStatus", from == null ? null : from.name());
        p.put("currentStatus", to.name());
        p.put("moveInDate", c.getMoveInDate() == null ? null : c.getMoveInDate().toString());
        p.put("moveOutDate", c.getMoveOutDate() == null ? null : c.getMoveOutDate().toString());
        p.put("deposit", c.getDeposit());                      // 보증금 (DRAFT 생성 때부터 값이 있음)
        outboxRepository.save(OutboxEvent.contractStatusChanged(c.getId(), statusEventId, jsonMapper.writeValueAsString(p)));

        if (from == ContractStatus.PAID && to == ContractStatus.COMPLETED) {
            UUID completedEventId = UUID.randomUUID();
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("eventId", completedEventId.toString());
            q.put("occurredAt", occurredAt);
            q.put("applicationId", c.getId());
            q.put("userId", c.getUserId());                    // 입주자가 되는 회원
            q.put("buildingId", c.getBuildingId());
            q.put("buildingName", c.getBuildingName());
            q.put("roomId", c.getRoomId());
            q.put("roomName", c.getRoomName());
            q.put("moveInDate", c.getMoveInDate() == null ? null : c.getMoveInDate().toString());
            q.put("moveOutDate", c.getMoveOutDate() == null ? null : c.getMoveOutDate().toString());
            outboxRepository.save(OutboxEvent.contractCompleted(c.getId(), completedEventId, jsonMapper.writeValueAsString(q)));
        }

        // 메일 (커밋 후 ContractMailListener가 발송)
        MailType.forTransition(from, to)
                .ifPresent(type -> eventPublisher.publishEvent(new ContractMailEvent(type, ContractMailData.from(c))));
    }
}
