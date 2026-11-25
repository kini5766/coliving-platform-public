package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.PaymentGatewayException;
import com.coliving.platform.common.exception.PaymentRejectedException;
import com.coliving.platform.contract.dto.payment.KakaoPayApproveRequest;
import com.coliving.platform.contract.dto.payment.PaymentResultResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** API로 재현하기 어려운 경합·장애 경로. "돈이 빠져나갔는데 기록이 없는" 경우가 없는지 본다 */
class PaymentServiceTest {

    private static final String NO = "CT-20261102-0001";
    private static final String ORDER = NO + "-abcd1234";
    private static final String TID = "T1";
    private static final long AMOUNT = 1_780_000L;

    private final ContractRepository contracts = mock(ContractRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final HistoryRepository history = mock(HistoryRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final KakaoPayClient kakao = mock(KakaoPayClient.class);
    private final KakaoPayClient.Approved approved = new KakaoPayClient.Approved("MONEY", Instant.now());
    private final KakaoPayApproveRequest request = new KakaoPayApproveRequest(ORDER, "token");

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

        Contract contract = approvedContract();
        when(contracts.findByApplicationNo(NO)).thenReturn(Optional.of(contract));

        payment = Payment.start(10L, ORDER, AMOUNT);
        ReflectionTestUtils.setField(payment, "id", 20L);
        payment.saveTid(TID);
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(payment));
        when(payments.findById(20L)).thenReturn(Optional.of(payment));
    }

    private static Contract approvedContract() {
        Contract c = mock(Contract.class);
        when(c.getId()).thenReturn(10L);
        when(c.getUserId()).thenReturn(1L);
        when(c.getRoomId()).thenReturn(1L);
        when(c.getApplicationNo()).thenReturn(NO);
        when(c.isOwnedBy(1L)).thenReturn(true);
        when(c.getStatus()).thenReturn(ContractStatus.APPROVED);
        when(c.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(c.getMoveInDate()).thenReturn(LocalDate.of(2027, 1, 1));
        when(c.getMoveOutDate()).thenReturn(LocalDate.of(2027, 6, 30));
        return c;
    }

    @Test
    void 정상_승인이면_결제완료_이력_아웃박스를_함께_저장한다() {
        when(kakao.approve(TID, ORDER, "1", "token")).thenReturn(approved);
        when(payments.markPaidIfPending(eq(20L), eq("MONEY"), any(), any())).thenReturn(1);
        when(contracts.markPaidIfApproved(eq(10L), any())).thenReturn(1);

        PaymentResultResponse res = service.approveKakaoPay(1L, NO, request);

        assertThat(res.status()).isEqualTo(ContractStatus.PAID);
        assertThat(res.paidAmount()).isEqualTo(AMOUNT);
        verify(history).save(any());
        verify(outbox).save(any());
        verify(kakao, never()).cancel(anyString(), anyLong());
    }

    @Test
    void 결제완료_메시지에_eventId와_한국시간_occurredAt이_들어간다() {
        when(kakao.approve(TID, ORDER, "1", "token")).thenReturn(approved);
        when(payments.markPaidIfPending(eq(20L), eq("MONEY"), any(), any())).thenReturn(1);
        when(contracts.markPaidIfApproved(eq(10L), any())).thenReturn(1);

        service.approveKakaoPay(1L, NO, request);

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(saved.capture());
        var body = JsonMapper.builder().build().readTree(saved.getValue().getPayload());
        // 본문의 eventId = event_id 컬럼 값
        assertThat(body.get("eventId").asString()).isEqualTo(saved.getValue().getEventId().toString());
        // 결제 승인 시각을 한국 시간(+09:00)으로, 초까지
        String expected = approved.approvedAt().truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.ofHours(9))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertThat(body.get("occurredAt").asString()).isEqualTo(expected).endsWith("+09:00");
    }

    @Test
    void 승인_응답을_못_받아도_주문이_결제됐으면_완료_처리한다() {
        when(kakao.approve(any(), any(), any(), any())).thenThrow(new PaymentGatewayException("timeout", null));
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("SUCCESS_PAYMENT", approved));
        when(payments.markPaidIfPending(eq(20L), any(), any(), any())).thenReturn(1);
        when(contracts.markPaidIfApproved(eq(10L), any())).thenReturn(1);

        assertThat(service.approveKakaoPay(1L, NO, request).status()).isEqualTo(ContractStatus.PAID);
    }

    @Test
    void 승인도_주문조회도_실패하면_PENDING으로_두고_503() {
        when(kakao.approve(any(), any(), any(), any())).thenThrow(new PaymentGatewayException("timeout", null));
        when(kakao.order(TID)).thenThrow(new PaymentGatewayException("down", null));

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request)).isInstanceOf(PaymentGatewayException.class);

        verify(payments, never()).closeIfPending(any(), any(), any(), any());
        verify(payments, never()).markPaidIfPending(any(), any(), any(), any());
    }

    @Test
    void 거절됐는데_주문이_실패_상태면_FAILED로_닫는다() {
        when(kakao.approve(any(), any(), any(), any())).thenThrow(new PaymentRejectedException("거절"));
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("FAIL_PAYMENT", null));

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request)).isInstanceOf(PaymentRejectedException.class);

        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.FAILED), any(), any());
    }

    @Test
    void 거절됐는데_주문이_진행_중이면_결제를_닫지_않는다() {
        when(kakao.approve(any(), any(), any(), any())).thenThrow(new PaymentRejectedException("토큰 오류"));
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("AUTH_PASSWORD", null));

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request)).isInstanceOf(PaymentRejectedException.class);

        verify(payments, never()).closeIfPending(any(), any(), any(), any());
    }

    @Test
    void 승인_후_신청_상태가_바뀌었으면_자동_환불한다() {
        when(kakao.approve(any(), any(), any(), any())).thenReturn(approved);
        when(payments.markPaidIfPending(eq(20L), any(), any(), any())).thenAnswer(i -> {
            ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
            return 1;
        });
        when(contracts.markPaidIfApproved(eq(10L), any())).thenReturn(0);

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("자동으로 취소");

        verify(kakao).cancel(TID, AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(outbox, never()).save(any());
    }

    @Test
    void 환불_호출이_실패하면_CANCEL_REQUESTED로_남긴다() {
        when(kakao.approve(any(), any(), any(), any())).thenReturn(approved);
        when(payments.markPaidIfPending(eq(20L), any(), any(), any())).thenAnswer(i -> {
            ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
            return 1;
        });
        when(contracts.markPaidIfApproved(eq(10L), any())).thenReturn(0);
        doThrow(new PaymentGatewayException("down", null)).when(kakao).cancel(TID, AMOUNT);

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("자동으로 다시 시도");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
    }

    @Test
    void 같은_신청의_다른_결제가_먼저_성공했으면_이번_결제를_취소한다() {
        when(kakao.approve(any(), any(), any(), any())).thenReturn(approved);
        when(payments.markPaidIfPending(eq(20L), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uq_payment_one_paid"));

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request)).isInstanceOf(IllegalStateException.class);

        verify(kakao).cancel(TID, AMOUNT);
        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.FAILED), any(), any());
    }

    @Test
    void 다른_결제가_먼저_성공했고_환불도_실패하면_재시도_대상으로_넣는다() {
        when(kakao.approve(any(), any(), any(), any())).thenReturn(approved);
        when(payments.markPaidIfPending(eq(20L), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uq_payment_one_paid"));
        doThrow(new PaymentGatewayException("down", null)).when(kakao).cancel(TID, AMOUNT);

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("자동으로 다시 시도");

        // CANCEL_REQUESTED로 바꿔 환불 재시도 스케줄러가 다시 환불하게 한다 (FAILED로 닫지 않음)
        verify(payments).markRefundPending(eq(20L), any(), any());
        verify(payments, never()).closeIfPending(any(), any(), any(), any());
    }

    @Test
    void 동시_요청이_먼저_처리했으면_환불하지_않고_같은_결과를_준다() {
        when(kakao.approve(any(), any(), any(), any())).thenThrow(new PaymentRejectedException("이미 승인"));
        when(kakao.order(TID)).thenReturn(new KakaoPayClient.Order("SUCCESS_PAYMENT", approved));
        when(payments.markPaidIfPending(eq(20L), any(), any(), any())).thenAnswer(i -> {
            ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
            return 0;
        });

        assertThat(service.approveKakaoPay(1L, NO, request).status()).isEqualTo(ContractStatus.PAID);

        verify(kakao, never()).cancel(anyString(), anyLong());
    }

    @Test
    void 기한이_지났으면_카카오_승인을_부르지_않는다() {
        Contract expired = approvedContract();
        when(expired.getExpiresAt()).thenReturn(Instant.now().minusSeconds(1));
        when(contracts.findByApplicationNo(NO)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.approveKakaoPay(1L, NO, request)).isInstanceOf(IllegalStateException.class);

        verify(kakao, never()).approve(any(), any(), any(), any());
        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.FAILED), any(), any());
    }

    @Test
    void 결제창_취소는_PENDING만_닫는다() {
        service.abort(1L, NO, new com.coliving.platform.contract.dto.payment.PaymentAbortRequest(ORDER, "cancel"));

        verify(payments).closeIfPending(eq(20L), eq(PaymentStatus.CANCELED), isNull(), any());
    }
}
