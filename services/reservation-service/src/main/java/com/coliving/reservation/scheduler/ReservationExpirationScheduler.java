
package com.coliving.reservation.scheduler;

import com.coliving.reservation.entity.ResourceReservation;
import com.coliving.reservation.entity.SharedResource;
import com.coliving.reservation.repository.ResourceReservationRepository;
import com.coliving.reservation.repository.SharedResourceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final ResourceReservationRepository resourceReservationRepository;
    private final SharedResourceRepository sharedResourceRepository;

    // 1분마다 예약 상태 자동 확인
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireReservations() {

        // 1. 승인된 예약 목록 조회
        List<ResourceReservation> reservations =
                resourceReservationRepository.findByStatus("APPROVED");

        // 2. 현재 날짜와 시간
        LocalDateTime now = LocalDateTime.now();

        // 3. 예약 목록을 하나씩 확인
        for (ResourceReservation reservation : reservations) {

            // 4. 예약에 연결된 공용 자원 조회
            SharedResource resource = sharedResourceRepository
                    .findById(reservation.getResourceId())
                    .orElse(null);

            if (resource == null) {
                continue;
            }

            // 5. 예약 시작 날짜와 시간
            LocalDateTime startDateTime = LocalDateTime.of(
                    reservation.getReservationDate(),
                    reservation.getStartTime()
            );

            // 6. 예약 종료 날짜와 시간
            LocalDateTime endDateTime = LocalDateTime.of(
                    reservation.getReservationDate(),
                    reservation.getEndTime()
            );

            // ==========================================
            // 7. QR 미체크인 30분 자동 취소 (신규)
            // ==========================================

            // 공용 공간과 물품 모두 적용
            boolean isCheckInRequired =
                    "SPACE".equals(resource.getResourceType())
                            || "ITEM".equals(resource.getResourceType());

            LocalDateTime checkInDeadline =
                    startDateTime.plusMinutes(30);

            if (isCheckInRequired
                    && reservation.getCheckedInAt() == null
                    && !now.isBefore(checkInDeadline)) {

                // 체크인하지 않은 예약 자동 취소
                reservation.setStatus("CANCELED");

                // 취소된 예약은 이후 만료 및 연체 처리 제외
                continue;
            }

            // ==========================================
            // 8. 예약 종료 시간 확인
            // ==========================================

            // 아직 종료 시간이 지나지 않았다면 건너뛰기
            if (now.isBefore(endDateTime)) {
                continue;
            }

            // ==========================================
            // 9. 공간 예약 자동 만료 (기존 기능)
            // ==========================================

            if ("SPACE".equals(resource.getResourceType())) {

                reservation.setStatus("EXPIRED");
            }

            // ==========================================
            // 10. 물품 미반납 연체 처리 (기존 기능)
            // ==========================================

            if ("ITEM".equals(resource.getResourceType())
                    && reservation.getReturnedAt() == null) {

                reservation.setStatus("OVERDUE");
                reservation.setIsOverdue(true);
            }
        }
    }
}
