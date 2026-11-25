package com.coliving.platform.contract.scheduler;

import com.coliving.platform.contract.entity.OutboxEvent;
import com.coliving.platform.contract.enums.OutboxStatus;
import com.coliving.platform.contract.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 아웃박스 발행: outbox_event의 PENDING을 Kafka로 보낸다.
 *
 * 흐름
 *   1) PENDING을 id 순서(오래된 것부터)로 읽는다
 *   2) Kafka로 보내고 브로커 응답(ack)까지 기다린다
 *   3) 성공하면 PUBLISHED, 실패하면 재시도 횟수를 올린다 (max-retry 번 실패하면 FAILED)
 *
 * 규칙
 *   - 한 건이라도 실패하면 이번 회차는 거기서 멈춘다 (순서 유지, 브로커 장애 때 재시도 횟수를 한꺼번에 태우지 않음)
 *   - 최소 1번 전달이다: Kafka 전송은 됐는데 PUBLISHED 저장이 실패하면 다음 회차에 같은 메시지가 다시 간다
 *     → 소비자는 payload의 eventId(또는 paymentId)로 중복을 걸러야 한다
 *   - FAILED는 자동으로 다시 보내지 않는다. 원인을 해결한 뒤 status='PENDING', retry_count=0 으로 되돌리면 다시 발행된다
 *
 * 설정 (application.yaml, 없으면 아래 기본값)
 *   app.outbox.publisher.enabled      : 켜기/끄기 (기본 true)
 *   app.outbox.publisher.interval     : 회차 간격 (기본 PT10S)
 *   app.outbox.publisher.batch-size   : 한 번에 보낼 건수 (기본 50)
 *   app.outbox.publisher.max-retry    : FAILED로 바꾸는 실패 횟수 (기본 100)
 *   app.outbox.publisher.send-timeout : 건당 ack 대기 시간 (기본 PT5S)
 *   app.kafka.topics.payment-completed: 결제 완료 토픽 (기본 payment.completed, config 저장소에도 같은 값)
 *   app.kafka.topics.payment-refunded : 환불 완료 토픽 (기본 payment.refunded)
 *
 * ⚠ 서버를 2대 이상 띄우면 같은 이벤트가 중복 발행될 수 있다 (소비자 중복 처리로 안전). 그때 ShedLock 적용.
 *
 * @author 한성현
 */
@Component
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishScheduler.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate tx;
    private final String paymentCompletedTopic;
    private final String paymentRefundedTopic;
    private final String statusChangedTopic;
    private final String contractCompletedTopic;
    private final int batchSize;
    private final int maxRetry;
    private final Duration sendTimeout;

    public OutboxPublishScheduler(OutboxEventRepository outboxRepository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  TransactionTemplate tx,
                                  @Value("${app.kafka.topics.payment-completed:payment.completed}") String paymentCompletedTopic,
                                  @Value("${app.kafka.topics.payment-refunded:payment.refunded}") String paymentRefundedTopic,
                                  @Value("${app.kafka.topics.status-changed:contract.status-changed}") String statusChangedTopic,
                                  @Value("${app.kafka.topics.contract-completed:contract.completed}") String contractCompletedTopic,
                                  @Value("${app.outbox.publisher.batch-size:50}") int batchSize,
                                  @Value("${app.outbox.publisher.max-retry:100}") int maxRetry,
                                  @Value("${app.outbox.publisher.send-timeout:PT5S}") Duration sendTimeout) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.tx = tx;
        this.paymentCompletedTopic = paymentCompletedTopic;
        this.paymentRefundedTopic = paymentRefundedTopic;
        this.statusChangedTopic = statusChangedTopic;
        this.contractCompletedTopic = contractCompletedTopic;
        this.batchSize = batchSize;
        this.maxRetry = maxRetry;
        this.sendTimeout = sendTimeout;
    }

    // fixedDelay: 앞 회차가 끝나고 interval 뒤에 다음 회차 (회차가 겹치지 않는다)
    // initialDelay: 서버가 뜨자마자 보내지 않고 30초 뒤부터 (Kafka 연결이 잡힐 시간)
    @Scheduled(fixedDelayString = "${app.outbox.publisher.interval:PT10S}", initialDelay = 30_000)
    public void publishPending() {
        List<OutboxEvent> targets = outboxRepository.findByStatusOrderByIdAsc(
                OutboxStatus.PENDING, PageRequest.of(0, batchSize));
        for (OutboxEvent event : targets) {
            if (!publish(event)) break;
        }
    }

    /** 한 건 발행. 실패하면 false (호출한 쪽이 이번 회차를 멈춘다) */
    private boolean publish(OutboxEvent event) {
        Long id = event.getId();
        try {
            kafkaTemplate.send(topicOf(event.getEventType()), String.valueOf(event.getAggregateId()), event.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure(id, e);
            return false;
        } catch (Exception e) {
            recordFailure(id, e);
            return false;
        }
        tx.executeWithoutResult(st -> outboxRepository.findById(id).orElseThrow().markPublished());
        log.info("아웃박스 발행 id={} type={} applicationId={}", id, event.getEventType(), event.getAggregateId());
        return true;
    }

    private String topicOf(String eventType) {
        if (OutboxEvent.PAYMENT_COMPLETED.equals(eventType)) return paymentCompletedTopic;
        if (OutboxEvent.PAYMENT_REFUNDED.equals(eventType)) return paymentRefundedTopic;
        if (OutboxEvent.CONTRACT_STATUS_CHANGED.equals(eventType)) return statusChangedTopic;
        if (OutboxEvent.CONTRACT_COMPLETED.equals(eventType)) return contractCompletedTopic;
        throw new IllegalStateException("발행 토픽이 정해지지 않은 이벤트 타입: " + eventType);
    }

    private void recordFailure(Long id, Exception e) {
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        tx.executeWithoutResult(st -> outboxRepository.findById(id).orElseThrow().recordFailure(error, maxRetry));
        log.warn("아웃박스 발행 실패 (다음 회차에 다시 시도) id={} {}", id, error);
    }
}
