package com.coliving.dashboard.activitylog.service;

import com.coliving.dashboard.activitylog.document.ActivityLogDocument;
import com.coliving.dashboard.activitylog.dto.ActivityLogPageResponse;
import com.coliving.dashboard.activitylog.dto.ActivityLogResponse;
import com.coliving.dashboard.activitylog.repository.ActivityLogRepository;
import com.coliving.dashboard.kafka.dto.ContractStatusChangedEvent;
import com.coliving.dashboard.kafka.dto.PaymentCompletedEvent;
import com.coliving.dashboard.kafka.dto.PaymentRefundedEvent;
import com.coliving.dashboard.kafka.dto.ReservationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 계약·결제·환불·예약 이벤트를 활동 로그로 저장하고, 화면에 보여줄 최근 목록을 조회한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private static final Set<String> ACTIVITY_TYPES = Set.of("CONTRACT", "RESERVATION", "MOVE_IN", "PAYMENT");
    private static final int HISTORY_MAX_SIZE = 50;
    private static final int ES_RESULT_WINDOW = 10_000;   // ES가 한 번에 건너뛸 수 있는 한도 (page * size)

    private final ActivityLogRepository repository;

    /** 계약 상태 변경 이벤트를 활동 로그로 저장한다. */
    public void saveContractActivity(ContractStatusChangedEvent event) {
        String place = event.buildingName() + " " + event.roomName();
        String type = "CONTRACT";
        String status;
        String content;

        switch (event.currentStatus()) {
            case "SUBMITTED" -> {                       // 계약 신청 접수
                status = "PENDING";
                content = place + " 계약 신청 (" + event.applicantName() + ")";
            }
            case "APPROVED" -> {                        // 관리자 승인
                status = "COMPLETED";
                content = place + " 계약 승인";
            }
            case "REJECTED" -> {                        // 관리자 반려
                status = "COMPLETED";
                content = place + " 계약 반려";
            }
            case "CANCELED" -> {                        // 심사 중 신청자가 취소
                status = "COMPLETED";
                content = place + " 계약 신청 취소";
            }
            case "COMPLETED" -> {                       // 입주 확정
                type = "MOVE_IN";
                status = "COMPLETED";
                content = place + " 입주 완료";
            }
            case "TERMINATION_REQUESTED" -> {           // 퇴실 요청
                status = "PENDING";
                content = place + " 퇴실 요청 (" + event.applicantName() + ")";
            }
            case "TERMINATED" -> {                      // 계약 해지 확정
                status = "COMPLETED";
                content = place + " 계약 해지";
            }
            default -> {
                log.debug("활동 로그 대상이 아닌 계약 상태: {}", event.currentStatus());
                return;
            }
        }

        save(type + "-" + event.applicationId(), type, content, status,
                event.eventId(), event.buildingId(), event.buildingName(),
                "contract-service", String.valueOf(event.applicationId()),
                event.occurredAt().toInstant());
    }

    /** 결제 완료 이벤트를 활동 로그로 저장한다. 결제 ID 기준으로 1건씩 쌓인다. */
    public void savePaymentActivity(PaymentCompletedEvent event) {
        String content = event.buildingName() + " " + event.roomName()
                + " 결제 완료 (" + event.applicantName() + ", "
                + String.format("%,d", event.amount()) + "원)";

        save("PAYMENT-" + event.paymentId(), "PAYMENT", content, "COMPLETED",
                event.eventId(), event.buildingId(), event.buildingName(),
                "contract-service", String.valueOf(event.paymentId()),
                event.occurredAt().toInstant());
    }

    /** 환불 완료 이벤트를 활동 로그로 저장한다. 결제 완료 로그(PAYMENT-{id})와 섞이지 않게 REFUND-{결제 ID}로 쌓는다. */
    public void savePaymentRefundActivity(PaymentRefundedEvent event) {
        String content = event.buildingName() + " " + event.roomName()
                + " 환불 완료 (" + event.applicantName() + ", "
                + String.format("%,d", event.refundAmount()) + "원)";

        save("REFUND-" + event.paymentId(), "PAYMENT", content, "COMPLETED",
                event.eventId(), event.buildingId(), event.buildingName(),
                "contract-service", String.valueOf(event.paymentId()),
                event.occurredAt().toInstant());
    }

    /**
     * 예약 상태 변경 이벤트를 활동 로그로 저장한다.
     * 공용 자원 예약은 승인·대기 없이 만들어지는 즉시 확정되므로 상태는 항상 완료이고,
     * 예약 서비스는 APPROVED(예약 확정)와 CANCELED(취소)만 보낸다.
     */
    public void saveReservationActivity(ReservationStatusChangedEvent event) {
        String content;
        String who = "회원 " + event.userId();

        switch (event.reservationStatus()) {
            case "APPROVED" -> content = event.resourceName() + " 사용 예약 (" + who + ")";
            case "CANCELED" -> content = event.resourceName() + " 사용 예약 취소 (" + who + ")";
            default -> {
                log.debug("활동 로그 대상이 아닌 예약 상태: {}", event.reservationStatus());
                return;
            }
        }

        save("RESERVATION-" + event.reservationId(), "RESERVATION", content, "COMPLETED",
                event.eventId(), event.buildingId(), event.buildingName(),
                "amenity-service", String.valueOf(event.reservationId()),
                event.occurredAt().toInstant());
    }

    /** 건물별 최근 활동 로그를 최근 변경 순으로 조회한다. */
    public List<ActivityLogResponse> getRecent(Long buildingId, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        return repository.findByBuildingIdOrderByUpdatedAtDesc(buildingId, PageRequest.of(0, limit))
                .stream()
                .map(ActivityLogResponse::from)
                .toList();
    }

    /**
     * "더보기" 팝업용 활동 로그를 최근 변경 순으로 한 페이지씩 조회한다.
     * 활동 유형을 주면 그 유형만 조회한다.
     */
    public ActivityLogPageResponse getHistory(Long buildingId, String type, int page, int size) {
        String activityType = (type == null || type.isBlank()) ? null : type;
        if (activityType != null && !ACTIVITY_TYPES.contains(activityType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 활동 유형입니다: " + activityType);
        }

        int safeSize = Math.min(Math.max(size, 1), HISTORY_MAX_SIZE);
        int safePage = Math.max(page, 0);
        if ((long) (safePage + 1) * safeSize > ES_RESULT_WINDOW) {
            return new ActivityLogPageResponse(List.of(), false);
        }

        PageRequest pageable = PageRequest.of(safePage, safeSize);
        Page<ActivityLogDocument> result = activityType == null
                ? repository.searchByBuildingIdOrderByUpdatedAtDesc(buildingId, pageable)
                : repository.searchByBuildingIdAndActivityTypeOrderByUpdatedAtDesc(buildingId, activityType, pageable);

        List<ActivityLogResponse> items = result.getContent().stream()
                .map(ActivityLogResponse::from)
                .toList();
        return new ActivityLogPageResponse(items, result.hasNext());
    }

    /** 활동 로그를 ES에 저장한다. */
    private void save(String id, String type, String content, String status,
                      String eventId, Long buildingId, String buildingName,
                      String sourceService, String sourceRefId, Instant occurredAt) {

        // 같은 건이 이미 있으면 최초 발생 시간은 유지
        Instant firstOccurredAt = repository.findById(id)
                .map(ActivityLogDocument::getOccurredAt)
                .orElse(occurredAt);

        try {
            repository.save(ActivityLogDocument.builder()
                    .id(id)
                    .version(occurredAt.toEpochMilli())
                    .eventId(eventId)
                    .buildingId(buildingId)
                    .buildingName(buildingName)
                    .activityType(type)
                    .content(content)
                    .status(status)
                    .sourceService(sourceService)
                    .sourceRefId(sourceRefId)
                    .occurredAt(firstOccurredAt)
                    .updatedAt(occurredAt)
                    .build());
        } catch (OptimisticLockingFailureException e) {
            log.info("이미 같거나 더 최신인 활동 로그가 있어 무시합니다: id={}", id);
        }
    }

    /** 이미 활동 로그가 있는 결제인지 확인한다. */
    public boolean existsPayment(Long paymentId) {
        return repository.existsById("PAYMENT-" + paymentId);
    }

    /** 이미 활동 로그가 있는 환불인지 확인한다. */
    public boolean existsPaymentRefund(Long paymentId) {
        return repository.existsById("REFUND-" + paymentId);
    }
}