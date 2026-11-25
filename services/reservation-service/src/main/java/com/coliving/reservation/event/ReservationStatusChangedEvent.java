package com.coliving.reservation.event;

import com.coliving.reservation.entity.ResourceReservation;
import com.coliving.reservation.entity.SharedResource;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 예약 상태 변경 이벤트 (토픽 reservation.status-changed, 받는 서비스: dashboard).
 * 필드 이름과 타입은 dashboard-service API 명세서 3-2와 같다.
 *
 * reservationStatus 는 PENDING / APPROVED / REJECTED / CANCELED 중 하나만 보낸다.
 * (대시보드는 이 4개만 활동 로그로 남기고 나머지는 무시한다)
 */
public record ReservationStatusChangedEvent(
        String eventId,             // 이벤트 고유 ID (UUID)
        String occurredAt,          // 상태가 바뀐 시각 (한국 시간, 예: 2026-11-05T10:18:00.123+09:00)
        Long reservationId,         // 예약 ID
        Long buildingId,            // 건물 ID (공용 자원의 buildingId)
        String buildingName,        // 이 서비스는 건물명을 모르므로 항상 null
        String resourceName,        // 공용 자원 이름
        Long userId,                // 예약자 ID
        String reservationStatus    // PENDING / APPROVED / REJECTED / CANCELED
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 초 아래(밀리초)까지 보낸다. 대시보드는 occurredAt 의 밀리초 값을 버전으로 쓰기 때문에
    // 초 단위로 자르면 같은 초 안의 "승인 → 취소"가 같은 버전이 되어 취소가 무시된다.
    private static final DateTimeFormatter OCCURRED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /** 예약과 공용 자원으로 이벤트를 만든다. status 는 위 4개 값 중 하나. */
    public static ReservationStatusChangedEvent of(ResourceReservation reservation,
                                                   SharedResource resource,
                                                   String status) {
        return new ReservationStatusChangedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(KST).format(OCCURRED_AT),
                reservation.getId(),
                resource.getBuildingId(),
                null,
                resource.getResourceName(),
                reservation.getUserId(),
                status
        );
    }
}
