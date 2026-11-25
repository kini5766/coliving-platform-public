package com.coliving.platform.contract.scheduler;

import com.coliving.platform.contract.entity.OutboxEvent;
import com.coliving.platform.contract.enums.OutboxStatus;
import com.coliving.platform.contract.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 아웃박스 발행 (OutboxPublishScheduler) - 성공 / 실패하면 멈춤 / 실패가 쌓이면 FAILED */
class OutboxPublishSchedulerTest {

    private static final String TOPIC = "payment.completed";

    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    private OutboxEvent first;
    private OutboxEvent second;
    private OutboxPublishScheduler scheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(i -> {
            ((Consumer<TransactionStatus>) i.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());

        scheduler = new OutboxPublishScheduler(outbox, kafka, tx, TOPIC, "payment.refunded",
                "contract.status-changed", "contract.completed", 50, 3, Duration.ofSeconds(1));

        first = event(1L, 10L);
        second = event(2L, 11L);
        when(outbox.findByStatusOrderByIdAsc(eq(OutboxStatus.PENDING), any())).thenReturn(List.of(first, second));
        when(outbox.findById(1L)).thenReturn(Optional.of(first));
        when(outbox.findById(2L)).thenReturn(Optional.of(second));
    }

    private static OutboxEvent event(long id, long applicationId) {
        OutboxEvent e = OutboxEvent.paymentCompleted(applicationId, UUID.randomUUID(),
                "{\"applicationId\":" + applicationId + "}");
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    @Test
    void 대기_이벤트를_순서대로_보내고_PUBLISHED로_바꾼다() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        scheduler.publishPending();

        verify(kafka).send(eq(TOPIC), eq("10"), eq(first.getPayload()));      // 메시지 키 = 신청 ID
        verify(kafka).send(eq(TOPIC), eq("11"), eq(second.getPayload()));
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void 첫_건이_실패하면_횟수만_올리고_뒤의_건은_이번_회차에_보내지_않는다() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        scheduler.publishPending();

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(first.getRetryCount()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker down");
        verify(kafka, never()).send(eq(TOPIC), eq("11"), anyString());
        assertThat(second.getRetryCount()).isZero();
    }

    @Test
    void 실패가_한도에_닿으면_FAILED로_두고_더_이상_자동으로_보내지_않는다() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        scheduler.publishPending();
        scheduler.publishPending();
        scheduler.publishPending();                                  // max-retry 3

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(first.getRetryCount()).isEqualTo(3);
    }
}
