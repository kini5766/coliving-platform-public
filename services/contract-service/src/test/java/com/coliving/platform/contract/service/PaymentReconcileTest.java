package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.PaymentGatewayException;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractStatusHistory;
import com.coliving.platform.contract.entity.Payment;
import com.coliving.platform.contract.enums.ChangedByType;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.enums.PaymentStatus;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.ContractSignatureRepository;
import com.coliving.platform.contract.repository.HistoryRepository;
import com.coliving.platform.contract.repository.OutboxEventRepository;
import com.coliving.platform.contract.repository.PaymentRepository;
import com.coliving.platform.contract.service.PaymentService.ReconcileResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 PENDING 보정 (PaymentService.reconcile / retryRefund) — 가이드의 T1~T11.
 * 스케줄러 자체(@Scheduled 시간)는 테스트하지 않고, 한 건을 어떻게 처리하는지만 본다.
 */
class PaymentReconcileTest {

    private static final String ORDER = "CT-20261104-0001-abcd1234";
    private static final String TID = "T1";
    private static final long AMOUNT = 1_780_000L;
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final HistoryRepository history = mock(HistoryRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final KakaoPayClient kakao = mock(KakaoPayClient.class);
    private final KakaoPayClient.Approved approved = new KakaoPayClient.Approved("MONEY", Instant.now());

    private Payment payment;
    private PaymentService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(i ->
                ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        doAnswer(i -> {
            ((Consumer<TransactionStatus>) i.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());

        service = new PaymentService(contracts, mock(ContractDocumentRepository.class),
                mock(ContractSignatureRepository.class), payments, history, outbox, kakao,
                JsonMapper.builder().build(), tx, "http://f/approve", "http://f/pay?result=cancel", "http://f/pay?result=fail",
                mock(ContractStatusEvents.class));

        Contract contract = contract(ContractStatus.APPROVED);
        when(contracts.findById(10L)).thenReturn(Optional.of(contract));

        payment = pending(TID, Duration.ofMinutes(15));
        when(payments.findById(20L)).thenReturn(Optional.of(payment));
    }

    // 만든 지 age만큼 지난 PENDING 결제 (createdAt은 DB가 채우는 값이라 테스트에서 직접 넣는다)
    private static Payment pending(String tid, Duration age) {
        Payment p = Payment.start(10L, ORDER, AMOUNT);
        ReflectionTestUtils.setField(p, "id", 20L);
        ReflectionTestUtils.setField(p, "createdAt", Instant.now().minus(age));
        if (tid != null) p.saveTid(tid);
        return p;
    }

    private static Contract contract(ContractStatus status) {
        Contract c = mock(Contract.class);
        when(c.getId()).thenReturn(10L);
        when(c.getUserId()).thenReturn(1L);
        when(c.getRoomId()).thenReturn(1L);
        when(c.getApplicationNo()).thenReturn("CT-20261104-0001");
        when(c.getStatus()).thenReturn(status);
        when(c.getMoveInDate()).thenReturn(LocalDate.of(2027, 1, 1));
        when(c.getMoveOutDate()).thenReturn(LocalDate.of(2027, 6, 30));
        return c;
    }

    @Test
    void T1_tid가_없으면_카카오를_부르지_않고_FAILED() {
        payment = pending(null, Duration.ofMinutes(15));
        when(payments.findById(20L)).thenReturn(Optional.of(payment));

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.CLOSED);

        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.FAILED), any(), any());
        verify(kakao, never()).order(anyString());
    }

    @Test
    void T2_카카오에서_결제됐으면_PAID_이력은_SYSTEM_아웃박스_저장() {
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("SUCCESS_PAYMENT", approved));
        when(payments.markPaidIfPending(eq(20L), eq("MONEY"), any(), any())).thenReturn(1);
        when(contracts.markPaidIfApproved(eq(10L), any())).thenReturn(1);

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.PAID);

        ArgumentCaptor<ContractStatusHistory> saved = ArgumentCaptor.forClass(ContractStatusHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getChangedByType()).isEqualTo(ChangedByType.SYSTEM);
        verify(outbox).save(any());
    }

    @Test
    void T3_결제됐는데_신청이_이미_만료됐으면_자동_환불() {
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("SUCCESS_PAYMENT", approved));
        when(payments.markPaidIfPending(eq(20L), any(), any(), any())).thenAnswer(i -> {
            ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);   // DB UPDATE 흉내
            return 1;
        });
        when(contracts.markPaidIfApproved(eq(10L), any())).thenReturn(0);       // 신청이 APPROVED가 아님

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.REFUND_TRIED);

        verify(kakao).cancel(TID, AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(outbox, never()).save(any());
    }

    @Test
    void T4_사용자가_결제창을_닫았으면_CANCELED() {
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("QUIT_PAYMENT", null));

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.CLOSED);

        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.CANCELED), any(), any());
    }

    @Test
    void T5_카카오에서_실패했으면_FAILED() {
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("FAIL_PAYMENT", null));

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.CLOSED);

        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.FAILED), any(), any());
    }

    @Test
    void T6_진행중_상태로_시간이_너무_지났으면_FAILED() {
        payment = pending(TID, Duration.ofMinutes(40));
        when(payments.findById(20L)).thenReturn(Optional.of(payment));
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("READY", null));

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.CLOSED);

        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.FAILED), any(), any());
    }

    @Test
    void T7_진행중_상태이고_아직_시간이_남았으면_그대로_둔다() {
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("READY", null));    // 만든 지 15분

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.STILL_PENDING);

        verify(payments, never()).closeIfPending(any(), any(), any(), any());
    }

    @Test
    void T8_카카오_조회_장애면_예외_DB는_그대로() {
        when(kakao.order(TID)).thenThrow(new PaymentGatewayException("down", null));

        assertThatThrownBy(() -> service.reconcile(20L, TIMEOUT)).isInstanceOf(PaymentGatewayException.class);

        verify(payments, never()).closeIfPending(any(), any(), any(), any());
        verify(payments, never()).markPaidIfPending(any(), any(), any(), any());
    }

    @Test
    void T9_이미_처리된_결제는_건너뛴다() {
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);

        assertThat(service.reconcile(20L, TIMEOUT)).isEqualTo(ReconcileResult.SKIPPED);

        verify(kakao, never()).order(anyString());
    }

    @Test
    void T10_환불이_멈춘_결제는_카카오_취소_후_REFUNDED() {
        cancelRequested();
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("SUCCESS_PAYMENT", approved));

        assertThat(service.retryRefund(20L)).isTrue();

        verify(kakao).cancel(TID, AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(outbox, never()).save(any());            // 자동 환불은 신청 상태가 그대로라 환불 이벤트 없음
    }

    @Test
    void T11_카카오에서_이미_취소됐으면_취소를_다시_부르지_않고_REFUNDED() {
        cancelRequested();
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("CANCEL_PAYMENT", null));

        assertThat(service.retryRefund(20L)).isTrue();

        verify(kakao, never()).cancel(anyString(), anyLong());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    // PAID → 취소 선점(CANCEL_REQUESTED)까지 간 결제. 엔티티 메서드로 실제 전이를 밟는다
    private void cancelRequested() {
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
        payment.requestCancel("신청 상태 변경으로 자동 환불");
    }
}
