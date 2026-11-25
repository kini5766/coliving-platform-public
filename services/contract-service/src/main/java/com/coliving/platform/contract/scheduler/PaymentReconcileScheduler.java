package com.coliving.platform.contract.scheduler;

import com.coliving.platform.contract.entity.Payment;
import com.coliving.platform.contract.repository.PaymentRepository;
import com.coliving.platform.contract.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 결제 PENDING 보정 (COL-74).
 *
 * 스케줄러는 "언제, 무엇을" 고르는 일만 한다. 한 건을 어떻게 처리할지는 PaymentService.reconcile / retryRefund가 정한다.
 *   - 한 건이 실패해도 다음 건은 계속 처리한다 (try/catch를 건마다)
 *   - 실패한 건은 상태가 그대로라서 다음 회차에 다시 대상이 된다 → 따로 재시도 큐가 필요 없다
 *
 * 설정 (application.yaml, 없으면 아래 기본값)
 *   app.payment.reconcile.interval   : 몇 분마다 돌지          (기본 PT5M = 5분)
 *   app.payment.reconcile.min-age    : 만든 지 이만큼 지난 PENDING만 (기본 PT10M, 결제 중인 사람은 건드리지 않게)
 *   app.payment.reconcile.timeout    : 진행 중 상태로 이만큼 지나면 실패 처리 (기본 PT30M)
 *   app.payment.reconcile.batch-size : 한 번에 처리할 건수 (기본 50, 카카오 호출 수 제한)
 *
 * ⚠ 서버를 2대 이상 띄우면 두 대가 같은 결제를 동시에 처리할 수 있다.
 *   DB는 조건부 UPDATE라 안전하지만 카카오 조회·취소가 중복 호출된다 → 그때 ShedLock 적용 (shedlock 테이블은 이미 있음)
 *
 * @author 한성현
 */
@Component
public class PaymentReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileScheduler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final Duration minAge;
    private final Duration timeout;
    private final int batchSize;

    public PaymentReconcileScheduler(PaymentRepository paymentRepository,
                                     PaymentService paymentService,
                                     @Value("${app.payment.reconcile.min-age:PT10M}") Duration minAge,
                                     @Value("${app.payment.reconcile.timeout:PT30M}") Duration timeout,
                                     @Value("${app.payment.reconcile.batch-size:50}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.minAge = minAge;
        this.timeout = timeout;
        this.batchSize = batchSize;
    }

    // fixedDelay: 앞 회차가 "끝나고" interval 뒤에 다음 회차 (회차가 겹치지 않는다)
    // initialDelay: 서버가 뜨자마자 돌지 않고 1분 뒤부터
    @Scheduled(fixedDelayString = "${app.payment.reconcile.interval:PT5M}", initialDelay = 60_000)
    public void reconcilePending() {
        List<Payment> targets = paymentRepository.findPendingBefore(
                Instant.now().minus(minAge), PageRequest.of(0, batchSize));
        for (Payment p : targets) {
            try {
                PaymentService.ReconcileResult result = paymentService.reconcile(p.getId(), timeout);
                log.info("결제 보정 orderId={} → {}", p.getOrderId(), result);
            } catch (RuntimeException e) {
                log.warn("결제 보정 실패 (다음 회차에 재시도) orderId={}", p.getOrderId(), e);
            }
        }
    }

    // 자동 환불이 실패해서 CANCEL_REQUESTED에 멈춘 결제 다시 환불
    @Scheduled(fixedDelayString = "${app.payment.reconcile.interval:PT5M}", initialDelay = 120_000)
    public void retryRefunds() {
        List<Payment> targets = paymentRepository.findCancelRequestedBefore(
                Instant.now().minus(minAge), PageRequest.of(0, batchSize));
        for (Payment p : targets) {
            try {
                paymentService.retryRefund(p.getId());
                log.info("환불 재시도 성공 orderId={}", p.getOrderId());
            } catch (RuntimeException e) {
                log.error("환불 재시도 실패 - 계속 실패하면 수동 확인 필요 orderId={}", p.getOrderId(), e);
            }
        }
    }
}
