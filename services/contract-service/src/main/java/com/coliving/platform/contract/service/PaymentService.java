package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationAccessDeniedException;
import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.common.exception.PaymentGatewayException;
import com.coliving.platform.common.exception.PaymentRejectedException;
import com.coliving.platform.contract.dto.payment.KakaoPayApproveRequest;
import com.coliving.platform.contract.dto.payment.PaymentAbortRequest;
import com.coliving.platform.contract.dto.payment.PaymentInfoResponse;
import com.coliving.platform.contract.dto.payment.PaymentReadyRequest;
import com.coliving.platform.contract.dto.payment.PaymentReadyResponse;
import com.coliving.platform.contract.dto.payment.PaymentResultResponse;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.entity.ContractStatusHistory;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 3단계 카카오페이 결제.
 *
 *   ready   : 서명 완료 + APPROVED + 기한 안 확인 → payment PENDING 저장 → 카카오 ready → tid 저장
 *   approve : 결제창에서 돌아오면 카카오 approve → 한 트랜잭션에서 PAID + 이력 + 아웃박스
 *   abort   : 결제창 취소·실패 → PENDING을 CANCELED / FAILED로
 *
 * 카카오 호출은 트랜잭션 밖에서 한다 (기다리는 동안 DB 커넥션을 잡지 않는다).
 * 돈은 서버가 approve를 불러야 빠져나가므로, approve 전에 막힌 결제는 환불이 필요 없다.
 *
 * approve 응답을 못 받았거나 거절돼도 "실패"로 단정하지 않는다.
 * 응답만 유실됐거나, 새로고침으로 같은 요청이 겹쳤을 수 있다 → 주문 조회로 실제 결과를 확인한다.
 * 조회까지 안 되면 PENDING으로 두고 503 → 같은 화면을 새로고침하면 다시 확인한다.
 *
 * 승인은 됐는데 우리 쪽에 반영할 수 없으면(신청 상태 변경, 같은 신청의 다른 결제가 먼저 성공) 카카오 취소로 자동 환불한다.
 *
 * @author 한성현
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 초가 0이어도 "...:00+09:00"까지 항상 찍는다 (OffsetDateTime.toString()은 초 0이면 생략)
    private static final DateTimeFormatter OCCURRED_AT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String KAKAOPAY = "KAKAOPAY";
    private static final int ITEM_NAME_MAX = 100;

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractSignatureRepository signatureRepository;
    private final PaymentRepository paymentRepository;
    private final HistoryRepository historyRepository;
    private final OutboxEventRepository outboxRepository;
    private final KakaoPayClient kakaoPay;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate tx;
    private final String approvalUrl;
    private final String cancelUrl;
    private final String failUrl;
    private final ContractStatusEvents statusEvents;

    public PaymentService(ContractRepository contractRepository,
                          ContractDocumentRepository documentRepository,
                          ContractSignatureRepository signatureRepository,
                          PaymentRepository paymentRepository,
                          HistoryRepository historyRepository,
                          OutboxEventRepository outboxRepository,
                          KakaoPayClient kakaoPay,
                          JsonMapper jsonMapper,
                          TransactionTemplate tx,
                          @Value("${app.kakaopay.approval-url:http://localhost:5173/contract/pay/approve}") String approvalUrl,
                          @Value("${app.kakaopay.cancel-url:http://localhost:5173/contract/pay?result=cancel}") String cancelUrl,
                          @Value("${app.kakaopay.fail-url:http://localhost:5173/contract/pay?result=fail}") String failUrl,
                          ContractStatusEvents statusEvents) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.signatureRepository = signatureRepository;
        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
        this.outboxRepository = outboxRepository;
        this.kakaoPay = kakaoPay;
        this.jsonMapper = jsonMapper;
        this.tx = tx;
        this.approvalUrl = approvalUrl;
        this.cancelUrl = cancelUrl;
        this.failUrl = failUrl;
        this.statusEvents = statusEvents;
    }

    public PaymentInfoResponse getInfo(Long userId, String applicationNo) {
        Contract c = findOwned(userId, applicationNo);
        requirePayable(c);
        return new PaymentInfoResponse(
                c.getDeposit(),
                c.getMonthlyFee(),
                c.getFirstPayment(),
                c.getBuildingName(),
                c.getRoomName(),
                c.getManagementFee(),
                c.getMoveInDate(),
                c.getMoveOutDate(),
                List.of(KAKAOPAY),
                c.getExpiresAt() == null ? null : c.getExpiresAt().atOffset(KST),
                OffsetDateTime.now(KST)
        );
    }

    public PaymentReadyResponse ready(Long userId, String applicationNo, PaymentReadyRequest req) {
        if (req == null || !KAKAOPAY.equals(req.method())) {
            throw new IllegalArgumentException("지원하지 않는 결제 수단입니다.");
        }
        Contract c = findOwned(userId, applicationNo);
        requirePayable(c);

        String orderId = applicationNo + "-" + UUID.randomUUID().toString().substring(0, 8);
        long amount = c.getFirstPayment();                                  // 금액은 신청의 고정 금액만 쓴다
        Long paymentId = tx.execute(status ->
                paymentRepository.save(Payment.start(c.getId(), orderId, amount)).getId());

        KakaoPayClient.Ready ready;
        try {
            ready = kakaoPay.ready(new KakaoPayClient.ReadyCommand(
                    orderId, String.valueOf(userId), itemName(c), amount,
                    returnUrl(approvalUrl, applicationNo, orderId),
                    returnUrl(cancelUrl, applicationNo, orderId),
                    returnUrl(failUrl, applicationNo, orderId)));
        } catch (PaymentRejectedException | PaymentGatewayException e) {
            close(paymentId, PaymentStatus.FAILED, e.getMessage());
            throw e;
        }

        tx.executeWithoutResult(status -> paymentRepository.findById(paymentId).orElseThrow().saveTid(ready.tid()));
        return new PaymentReadyResponse(orderId, KAKAOPAY, ready.redirectPcUrl(), ready.redirectMobileUrl());
    }

    public PaymentResultResponse approveKakaoPay(Long userId, String applicationNo, KakaoPayApproveRequest req) {
        if (req == null || isBlank(req.orderId()) || isBlank(req.pgToken())) {
            throw new IllegalArgumentException("결제 승인 정보가 없습니다.");
        }
        Contract c = findOwned(userId, applicationNo);
        Payment p = findPayment(c, req.orderId());

        if (p.getStatus() == PaymentStatus.PAID) {
            return result(p.getAmount(), p.getApprovedAt());                // 새로고침으로 다시 온 요청
        }
        if (p.getStatus() != PaymentStatus.PENDING || p.getTid() == null) {
            throw new IllegalStateException("이미 처리된 결제입니다. 결제 화면에서 다시 시도해주세요.");
        }
        if (c.getStatus() != ContractStatus.APPROVED || expired(c)) {
            close(p.getId(), PaymentStatus.FAILED, "결제 가능 상태가 아님: " + c.getStatus());
            throw new IllegalStateException("결제할 수 없는 신청입니다. 결제 기한과 신청 상태를 확인해주세요.");
        }

        KakaoPayClient.Approved approved;
        try {
            approved = kakaoPay.approve(p.getTid(), p.getOrderId(), String.valueOf(userId), req.pgToken());
        } catch (PaymentRejectedException | PaymentGatewayException e) {
            approved = confirmByOrder(p, e);
        }
        return complete(c, p, approved, false);
    }

    public void abort(Long userId, String applicationNo, PaymentAbortRequest req) {
        if (req == null || isBlank(req.orderId())) {
            throw new IllegalArgumentException("주문번호가 없습니다.");
        }
        Contract c = findOwned(userId, applicationNo);
        Payment p = findPayment(c, req.orderId());
        boolean canceled = "cancel".equals(req.result());
        close(p.getId(),
                canceled ? PaymentStatus.CANCELED : PaymentStatus.FAILED,
                canceled ? null : "카카오페이 결제창 실패");
    }

    // approve가 실패한 뒤 실제 결과 확인. 결제됐으면 승인 정보를 돌려주고, 아니면 예외
    private KakaoPayClient.Approved confirmByOrder(Payment p, RuntimeException approveError) {
        KakaoPayClient.Order order;
        try {
            order = kakaoPay.order(p.getTid());
        } catch (PaymentRejectedException | PaymentGatewayException e) {
            throw new PaymentGatewayException("결제 결과를 확인하지 못했습니다: " + p.getOrderId(), e);
        }
        if (order.paid()) return order.approved();
        if (order.failed()) close(p.getId(), PaymentStatus.FAILED, approveError.getMessage());
        throw approveError;
    }

    private enum Outcome { PAID, ALREADY_PAID, REFUND_PAID, REFUND_UNRECORDED }

    // bySystem: 보정 스케줄러가 확정한 결제면 true → 상태 이력을 SYSTEM으로 남긴다 (사용자 승인은 false)
    private PaymentResultResponse complete(Contract c, Payment p, KakaoPayClient.Approved approved, boolean bySystem) {
        Long applicationId = c.getId();
        Long paymentId = p.getId();
        Long userId = c.getUserId();
        String tid = p.getTid();
        long amount = p.getAmount();
        Instant paidAt = approved.approvedAt() == null ? Instant.now() : approved.approvedAt();
        UUID eventId = UUID.randomUUID();                       // outbox_event.event_id = payload의 "eventId"
        String payload = paymentCompletedPayload(eventId, c, p, paidAt);

        Outcome outcome;
        try {
            outcome = tx.execute(status -> {
                Instant now = Instant.now();
                if (paymentRepository.markPaidIfPending(paymentId, approved.paymentMethod(), paidAt, now) == 0) {
                    PaymentStatus current = paymentRepository.findById(paymentId).orElseThrow().getStatus();
                    return current == PaymentStatus.PAID ? Outcome.ALREADY_PAID : Outcome.REFUND_UNRECORDED;
                }
                if (contractRepository.markPaidIfApproved(applicationId, now) == 0) {
                    paymentRepository.findById(paymentId).orElseThrow().requestCancel("신청 상태 변경으로 자동 환불");
                    return Outcome.REFUND_PAID;
                }
                statusEvents.publish(historyRepository.save(bySystem
                        ? ContractStatusHistory.system(applicationId, ContractStatus.APPROVED, ContractStatus.PAID)
                        : ContractStatusHistory.user(applicationId, ContractStatus.APPROVED, ContractStatus.PAID, userId)));
                outboxRepository.save(OutboxEvent.paymentCompleted(applicationId, eventId, payload));
                return Outcome.PAID;
            });
        } catch (DataIntegrityViolationException e) {
            outcome = Outcome.REFUND_UNRECORDED;                            // uq_payment_one_paid: 다른 결제가 먼저 성공
        }

        switch (outcome) {
            case PAID, ALREADY_PAID -> {
                return result(amount, paidAt);
            }
            case REFUND_PAID -> {
                boolean refunded = refund(p, tid, amount);
                if (refunded) {
                    tx.executeWithoutResult(status ->
                            paymentRepository.findById(paymentId).orElseThrow().markRefunded());
                }
                throw new IllegalStateException(refundMessage(refunded, "신청 상태가 변경돼"));
            }
            default -> {
                boolean refunded = refund(p, tid, amount);
                if (refunded) {
                    close(paymentId, PaymentStatus.FAILED, "중복 결제 자동 취소");
                } else {
                    // 카카오 취소 실패: 결제가 FAILED·CANCELED로 남으면 어느 스케줄러도 다시 보지 않아 돈이 묶인다
                    // → CANCEL_REQUESTED로 바꿔 환불 재시도 스케줄러(retryRefunds) 대상에 넣는다
                    tx.executeWithoutResult(status -> paymentRepository.markRefundPending(
                            paymentId, "자동 환불 재시도 대기 (이미 처리된 결제)", Instant.now()));
                }
                throw new IllegalStateException(refundMessage(refunded, "이미 처리된 결제라"));
            }
        }
    }

    // =====================================================================
    // PENDING 보정 (PaymentReconcileScheduler가 부른다)
    //   결제창을 닫았거나, approve 응답을 못 받고 사용자가 "다시 확인"도 안 눌러서 PENDING으로 남은 결제를
    //   카카오 주문 조회 결과에 맞춘다. 카카오 장애면 예외를 그대로 던진다 → 스케줄러가 다음 회차에 다시 시도
    // =====================================================================
    public enum ReconcileResult {
        SKIPPED,        // 그 사이 다른 요청이 처리함 (PENDING이 아님)
        PAID,           // 카카오에서 결제됨 → PAID 확정
        CLOSED,         // 실패·중단·시간 초과 → FAILED / CANCELED
        REFUND_TRIED,   // 결제는 됐는데 신청 상태가 바뀌어 자동 환불 시도 (성공·실패는 로그)
        STILL_PENDING   // 아직 결제창 진행 중 → 다음 회차에 다시 확인
    }

    public ReconcileResult reconcile(Long paymentId, Duration timeout) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow();
        if (p.getStatus() != PaymentStatus.PENDING) return ReconcileResult.SKIPPED;

        // ready 저장 직후 서버가 죽었거나 카카오 ready가 실패해서 거래 자체가 없음
        if (p.getTid() == null) {
            close(p.getId(), PaymentStatus.FAILED, "결제 준비 미완료(보정)");
            return ReconcileResult.CLOSED;
        }

        KakaoPayClient.Order order = kakaoPay.order(p.getTid());
        String s = order.status();

        if (order.paid()) {
            Contract c = contractRepository.findById(p.getApplicationId()).orElseThrow();
            try {
                complete(c, p, order.approved(), true);
                return ReconcileResult.PAID;
            } catch (IllegalStateException e) {                       // complete가 자동 환불 후 던지는 경우
                log.warn("결제 보정 중 자동 환불. orderId={}, {}", p.getOrderId(), e.getMessage());
                return ReconcileResult.REFUND_TRIED;
            }
        }
        if ("QUIT_PAYMENT".equals(s)) {
            close(p.getId(), PaymentStatus.CANCELED, "사용자 중단(보정)");
            return ReconcileResult.CLOSED;
        }
        if (order.failed()) {
            close(p.getId(), PaymentStatus.FAILED, "카카오 상태: " + s + "(보정)");
            return ReconcileResult.CLOSED;
        }
        if (s != null && s.endsWith("CANCEL_PAYMENT")) {             // CANCEL_PAYMENT, PART_CANCEL_PAYMENT
            log.warn("PENDING인데 카카오에서 취소된 결제. orderId={}, status={}", p.getOrderId(), s);
            close(p.getId(), PaymentStatus.FAILED, "카카오에서 취소된 결제(보정)");
            return ReconcileResult.CLOSED;
        }
        if (p.getCreatedAt() != null && p.getCreatedAt().plus(timeout).isBefore(Instant.now())) {
            close(p.getId(), PaymentStatus.FAILED, "결제 시간 초과(보정): " + s);
            return ReconcileResult.CLOSED;
        }
        return ReconcileResult.STILL_PENDING;
    }

    // =====================================================================
    // 입주 전 취소 (사용자가 누르면 바로 전액 환불)
    //   조건: 본인 신청 + 결제 완료(PAID·COMPLETED) + 오늘이 입주일 전날 이전
    //   1) 짧은 트랜잭션: 신청 → MOVE_IN_CANCEL_REQUESTED, 결제 → CANCEL_REQUESTED, 이력  (두 번 눌러도 한 번만 성공)
    //   2) 트랜잭션 밖: 카카오 전액 취소 → 성공하면 결제 REFUNDED + 신청 REFUNDED
    //   카카오가 실패하면 "요청은 됨" 상태로 두고 보정 스케줄러(retryRefunds)가 다시 환불한다 → 사용자는 다시 누를 필요 없음
    // =====================================================================
    public void cancelBeforeMoveIn(Long userId, String applicationNo) {
        Contract c = contractRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        if (!c.isOwnedBy(userId)) {
            throw new ApplicationAccessDeniedException("본인의 계약만 취소할 수 있습니다.");
        }
        ContractStatus from = c.getStatus();
        if (from != ContractStatus.PAID && from != ContractStatus.COMPLETED) {
            throw new IllegalStateException("결제가 완료된 계약만 입주 전 취소할 수 있습니다. (현재: " + from + ")");
        }
        if (!LocalDate.now(SEOUL).isBefore(c.getMoveInDate())) {
            throw new IllegalStateException("입주일 전날까지만 취소할 수 있습니다. 입주 후에는 퇴실 신청을 이용해주세요.");
        }

        Long paymentId = tx.execute(status -> {
            // 순서 중요: 벌크 UPDATE(clear)를 먼저 하고, 결제 엔티티는 그 뒤에 읽어서 바꾼다
            if (contractRepository.markMoveInCancelRequested(c.getId(), from, Instant.now()) == 0) {
                throw new IllegalStateException("이미 취소 처리 중이거나 상태가 바뀐 계약입니다. 새로고침 후 확인해주세요.");
            }
            statusEvents.publish(historyRepository.save(ContractStatusHistory.user(
                    c.getId(), from, ContractStatus.MOVE_IN_CANCEL_REQUESTED, userId)));
            Payment p = paymentRepository.findByApplicationIdAndStatus(c.getId(), PaymentStatus.PAID)
                    .orElseThrow(() -> new IllegalStateException("환불할 결제 내역을 찾을 수 없습니다."));   // → 위 변경도 롤백
            p.requestCancel("입주 전 취소 (사용자 요청)");
            return p.getId();
        });

        try {
            retryRefund(paymentId);
        } catch (PaymentRejectedException | PaymentGatewayException e) {
            log.warn("입주 전 취소 환불 실패 - 보정 스케줄러가 다시 시도. applicationNo={}", applicationNo, e);
        }
    }

    // CANCEL_REQUESTED 결제를 환불한다 (입주 전 취소, 자동 환불 실패분 재시도). 처리했으면 true
    //   카카오 장애·거절이면 예외 → 상태는 그대로라서 보정 스케줄러가 다음 회차에 다시 시도
    public boolean retryRefund(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow();
        if (p.getStatus() != PaymentStatus.CANCEL_REQUESTED) return false;

        KakaoPayClient.Order order = kakaoPay.order(p.getTid());
        if (!"CANCEL_PAYMENT".equals(order.status())) {               // 이미 취소됐으면 다시 부르지 않는다 (중복 취소는 400)
            kakaoPay.cancel(p.getTid(), p.getAmount());
        }
        Long applicationId = p.getApplicationId();
        tx.executeWithoutResult(st -> {
            // 입주 전 취소였으면 신청도 REFUNDED로 (자동 환불이면 0건 → 신청 상태 그대로)
            //   한 번도 PAID가 된 적 없는 결제(approvedAt 없음 = 중복 결제 자동 환불)는 신청과 무관하다
            //   → 같은 신청의 진짜 결제가 입주 전 취소 중이어도 신청 상태를 건드리지 않는다
            boolean moveInCancel = p.getApprovedAt() != null
                    && contractRepository.markRefundedIfCancelRequested(applicationId, Instant.now()) == 1;
            if (moveInCancel) {
                statusEvents.publish(historyRepository.save(ContractStatusHistory.system(
                        applicationId, ContractStatus.MOVE_IN_CANCEL_REQUESTED, ContractStatus.REFUNDED)));
            }
            Payment refunded = paymentRepository.findById(paymentId).orElseThrow();
            refunded.markRefunded();
            // 대시보드 최근 활동 로그용 환불 이벤트. 사용자가 요청한 입주 전 취소만 보낸다
            // (자동 환불·중복 결제 취소는 계약이 그대로라 "환불" 로그를 남기지 않는다)
            if (moveInCancel) {
                Contract c = contractRepository.findById(applicationId).orElseThrow();
                UUID eventId = UUID.randomUUID();
                outboxRepository.save(OutboxEvent.paymentRefunded(
                        applicationId, eventId, paymentRefundedPayload(eventId, c, refunded)));
            }
        });
        return true;
    }

    private boolean refund(Payment p, String tid, long amount) {
        try {
            kakaoPay.cancel(tid, amount);
            return true;
        } catch (PaymentRejectedException | PaymentGatewayException e) {
            log.error("결제 자동 취소 실패 - 수동 환불 필요. orderId={}, tid={}", p.getOrderId(), tid, e);
            return false;
        }
    }

    private static String refundMessage(boolean refunded, String reason) {
        return refunded
                ? reason + " 결제를 자동으로 취소했어요."
                : reason + " 결제를 취소해야 하는데 바로 처리하지 못했어요. 자동으로 다시 시도하니 잠시 후 확인해주세요.";
    }

    private void close(Long paymentId, PaymentStatus status, String reason) {
        String trimmed = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        tx.executeWithoutResult(s -> paymentRepository.closeIfPending(paymentId, status, trimmed, Instant.now()));
    }

    // 결제 완료 메시지 본문
    //   eventId    : 이벤트 고유 ID (outbox_event.event_id와 같은 값, 소비자 중복 처리용)
    //   occurredAt : 결제 확정 시각, 한국 시간 "2026-11-02T23:09:48+09:00"
    //   paidAt     : 같은 시각의 UTC 표기 "2026-11-02T14:09:48Z" (기존 필드라 유지)
    //   paymentId·applicantName·buildingId·buildingName·roomName : 대시보드(PaymentCompletedEvent)가 쓰는 값
    private String paymentCompletedPayload(UUID eventId, Contract c, Payment p, Instant paidAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("occurredAt", OCCURRED_AT.format(paidAt.truncatedTo(ChronoUnit.SECONDS).atOffset(KST)));
        payload.put("applicationId", c.getId());
        payload.put("applicationNo", c.getApplicationNo());
        payload.put("paymentId", p.getId());                    // 대시보드가 같은 결제를 두 번 세지 않는 기준
        payload.put("applicantName", c.getApplicantName());
        payload.put("buildingId", c.getBuildingId());
        payload.put("buildingName", c.getBuildingName());
        payload.put("roomName", c.getRoomName());
        payload.put("userId", c.getUserId());
        payload.put("roomId", c.getRoomId());
        payload.put("moveInDate", c.getMoveInDate().toString());
        payload.put("moveOutDate", c.getMoveOutDate().toString());
        payload.put("orderId", p.getOrderId());
        payload.put("amount", p.getAmount());
        payload.put("deposit", c.getDeposit());
        payload.put("paidAt", paidAt.toString());
        return jsonMapper.writeValueAsString(payload);
    }

    // 환불 완료 메시지 본문 (토픽 payment.refunded, 대시보드 최근 활동 로그) — 대시보드와 합의한 9개 필드
    //   occurredAt   : 환불 완료 시각(payment.canceled_at), 한국 시간
    //   refundAmount : 이번에 환불한 금액. 입주 전 취소는 전액이라 payment.amount
    private String paymentRefundedPayload(UUID eventId, Contract c, Payment p) {
        Instant refundedAt = p.getCanceledAt() == null ? Instant.now() : p.getCanceledAt();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("occurredAt", OCCURRED_AT.format(refundedAt.truncatedTo(ChronoUnit.SECONDS).atOffset(KST)));
        payload.put("paymentId", p.getId());
        payload.put("applicationId", c.getId());
        payload.put("applicantName", c.getApplicantName());
        payload.put("buildingId", c.getBuildingId());
        payload.put("buildingName", c.getBuildingName());
        payload.put("roomName", c.getRoomName());
        payload.put("refundAmount", p.getAmount());
        payload.put("deposit", c.getDeposit());
        return jsonMapper.writeValueAsString(payload);
    }

    private PaymentResultResponse result(long amount, Instant paidAt) {
        return new PaymentResultResponse(ContractStatus.PAID, KAKAOPAY, amount, paidAt.atOffset(KST));
    }

    private Contract findOwned(Long userId, String applicationNo) {
        Contract c = contractRepository.findByApplicationNo(applicationNo)
                .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
        if (!c.isOwnedBy(userId)) {
            throw new ApplicationAccessDeniedException("본인의 신청만 결제할 수 있습니다.");
        }
        return c;
    }

    private Payment findPayment(Contract c, String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .filter(p -> p.getApplicationId().equals(c.getId()))        // 다른 신청의 주문번호 차단
                .orElseThrow(() -> new ApplicationNotFoundException("결제 내역을 찾을 수 없습니다."));
    }

    // 서명 완료 + APPROVED + 기한 안
    private void requirePayable(Contract c) {
        if (c.getStatus() != ContractStatus.APPROVED) {
            throw new IllegalStateException("결제 대기 중인 신청이 아닙니다. (현재: " + c.getStatus() + ")");
        }
        if (expired(c)) {
            throw new IllegalStateException("결제 기한이 지난 신청입니다.");
        }
        ContractDocument d = documentRepository.findByApplicationId(c.getId())
                .orElseThrow(() -> new IllegalStateException("계약서가 없는 신청입니다."));
        if (!signatureRepository.existsByApplicationIdAndDocumentId(c.getId(), d.getId())) {
            throw new IllegalStateException("계약서에 서명한 뒤 결제할 수 있습니다.");
        }
    }

    private static boolean expired(Contract c) {
        return c.getExpiresAt() != null && Instant.now().isAfter(c.getExpiresAt());
    }

    private static String itemName(Contract c) {
        String name = c.getBuildingName() + " " + c.getRoomName() + " 보증금·첫 달 이용료";
        return name.length() > ITEM_NAME_MAX ? name.substring(0, ITEM_NAME_MAX) : name;
    }

    // 카카오 결제창에서 돌아올 주소 뒤에 신청번호·주문번호를 붙인다
    private static String returnUrl(String base, String applicationNo, String orderId) {
        return UriComponentsBuilder.fromUriString(base)
                .queryParam("applicationNo", applicationNo)
                .queryParam("orderId", orderId)
                .build()
                .encode()
                .toUriString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
