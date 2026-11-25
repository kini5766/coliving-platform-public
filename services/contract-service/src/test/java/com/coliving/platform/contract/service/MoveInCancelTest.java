package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.PaymentGatewayException;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.OutboxEvent;
import com.coliving.platform.contract.entity.Payment;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.enums.PaymentStatus;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.ContractSignatureRepository;
import com.coliving.platform.contract.repository.HistoryRepository;
import com.coliving.platform.contract.repository.OutboxEventRepository;
import com.coliving.platform.contract.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 입주 전 취소 (PaymentService.cancelBeforeMoveIn) — 사용자가 누르면 전액 환불, 입주일 전날까지 */
class MoveInCancelTest {

    private static final String NO = "CT-20261104-0001";
    private static final String TID = "T1";
    private static final long AMOUNT = 1_780_000L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final HistoryRepository history = mock(HistoryRepository.class);
    private final KakaoPayClient kakao = mock(KakaoPayClient.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);

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

        // 결제 완료(PAID)된 결제 1건
        payment = Payment.start(10L, NO + "-abcd1234", AMOUNT);
        ReflectionTestUtils.setField(payment, "id", 20L);
        payment.saveTid(TID);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
        ReflectionTestUtils.setField(payment, "approvedAt", java.time.Instant.parse("2026-11-01T05:00:00Z"));   // PAID 결제는 승인 시각이 있다
        when(payments.findByApplicationIdAndStatus(10L, PaymentStatus.PAID)).thenReturn(Optional.of(payment));
        when(payments.findById(20L)).thenReturn(Optional.of(payment));

        when(contracts.markMoveInCancelRequested(eq(10L), any(), any())).thenReturn(1);
        when(contracts.markRefundedIfCancelRequested(eq(10L), any())).thenReturn(1);
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("SUCCESS_PAYMENT", null));
    }

    private void givenContract(ContractStatus status, LocalDate moveIn) {
        Contract c = mock(Contract.class);
        when(c.getId()).thenReturn(10L);
        when(c.isOwnedBy(1L)).thenReturn(true);
        when(c.getStatus()).thenReturn(status);
        when(c.getMoveInDate()).thenReturn(moveIn);
        when(c.getApplicantName()).thenReturn("홍길동");
        when(c.getBuildingId()).thenReturn(1L);
        when(contracts.findByApplicationNo(NO)).thenReturn(Optional.of(c));
        when(contracts.findById(10L)).thenReturn(Optional.of(c));
    }

    @Test
    void 입주일_전날이면_카카오_전액취소_후_결제와_신청이_REFUNDED() {
        givenContract(ContractStatus.PAID, TODAY.plusDays(1));

        service.cancelBeforeMoveIn(1L, NO);

        verify(kakao).cancel(TID, AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(contracts).markRefundedIfCancelRequested(eq(10L), any());
        verify(history, times(2)).save(any());          // 사용자 취소 요청 + 시스템 환불 완료
    }

    @Test
    void 환불이_끝나면_대시보드용_PaymentRefunded_아웃박스를_남긴다() {
        givenContract(ContractStatus.PAID, TODAY.plusDays(1));

        service.cancelBeforeMoveIn(1L, NO);

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(saved.capture());
        OutboxEvent e = saved.getValue();
        assertThat(e.getEventType()).isEqualTo(OutboxEvent.PAYMENT_REFUNDED);
        assertThat(e.getAggregateId()).isEqualTo(10L);
        assertThat(e.getPayload())
                .contains("\"eventId\":\"" + e.getEventId() + "\"")
                .contains("\"paymentId\":20")
                .contains("\"applicantName\":\"홍길동\"")
                .contains("\"buildingId\":1")
                .contains("\"refundAmount\":" + AMOUNT)
                .doesNotContain("userId", "applicationNo", "refundType");
    }

    @Test
    void 계약완료_COMPLETED_상태도_취소할_수_있다() {
        givenContract(ContractStatus.COMPLETED, TODAY.plusDays(10));

        service.cancelBeforeMoveIn(1L, NO);

        verify(contracts).markMoveInCancelRequested(eq(10L), eq(ContractStatus.COMPLETED), any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void 입주일_당일이면_409_카카오는_부르지_않는다() {
        givenContract(ContractStatus.PAID, TODAY);

        assertThatThrownBy(() -> service.cancelBeforeMoveIn(1L, NO)).isInstanceOf(IllegalStateException.class);

        verify(kakao, never()).cancel(anyString(), anyLong());
        verify(contracts, never()).markMoveInCancelRequested(any(), any(), any());
    }

    @Test
    void 결제_전_신청은_입주전_취소_대상이_아니다() {
        givenContract(ContractStatus.APPROVED, TODAY.plusDays(5));

        assertThatThrownBy(() -> service.cancelBeforeMoveIn(1L, NO)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 남의_계약은_403() {
        givenContract(ContractStatus.PAID, TODAY.plusDays(5));

        assertThatThrownBy(() -> service.cancelBeforeMoveIn(2L, NO))
                .isInstanceOf(ApplicationAccessDeniedException.class);
    }

    @Test
    void 두번_눌러서_이미_취소중이면_409_카카오는_한번도_안_부른다() {
        givenContract(ContractStatus.PAID, TODAY.plusDays(5));
        when(contracts.markMoveInCancelRequested(eq(10L), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancelBeforeMoveIn(1L, NO)).isInstanceOf(IllegalStateException.class);

        verify(kakao, never()).cancel(anyString(), anyLong());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void 카카오_취소가_실패하면_에러없이_취소요청_상태로_남고_스케줄러가_재시도() {
        givenContract(ContractStatus.PAID, TODAY.plusDays(5));
        doThrow(new PaymentGatewayException("down", null)).when(kakao).cancel(TID, AMOUNT);

        service.cancelBeforeMoveIn(1L, NO);                     // 사용자에게는 예외를 던지지 않는다

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
        verify(contracts, never()).markRefundedIfCancelRequested(any(), any());
    }

    @Test
    void 승인된_적_없는_중복결제_환불은_신청_상태를_건드리지_않는다() {
        // 같은 신청의 진짜 결제가 입주 전 취소 중이어도, 중복 결제(approvedAt 없음) 환불 재시도는 신청을 REFUNDED로 바꾸면 안 된다
        givenContract(ContractStatus.MOVE_IN_CANCEL_REQUESTED, TODAY.plusDays(5));
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.CANCEL_REQUESTED);
        ReflectionTestUtils.setField(payment, "approvedAt", null);

        assertThat(service.retryRefund(20L)).isTrue();

        verify(kakao).cancel(TID, AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(contracts, never()).markRefundedIfCancelRequested(any(), any());
    }
}
