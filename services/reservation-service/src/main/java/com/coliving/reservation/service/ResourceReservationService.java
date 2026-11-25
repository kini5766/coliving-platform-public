
package com.coliving.reservation.service;

import com.coliving.reservation.dto.ResourceReservationRequestDto;
import com.coliving.reservation.entity.ResourceReservation;
import com.coliving.reservation.entity.SharedResource;
import com.coliving.reservation.repository.ResourceReservationRepository;
import com.coliving.reservation.repository.SharedResourceRepository;
import com.coliving.reservation.event.ReservationStatusChangedEvent;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceReservationService {

    private final ResourceReservationRepository resourceReservationRepository;
    private final SharedResourceRepository sharedResourceRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==========================================
    // 1. 본인 예약 조회
    // ==========================================
    private ResourceReservation findMyReservation(
            Long reservationId,
            Long userId) {

        return resourceReservationRepository
                .findByIdAndUserId(reservationId, userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "본인 예약을 찾을 수 없습니다."));
    }

    // ==========================================
    // 2. 내 예약 목록 조회
    // ==========================================
    @Transactional(readOnly = true)
    public List<ResourceReservation> getMyReservations(Long userId) {
        return resourceReservationRepository.findByUserId(userId);
    }

    // ==========================================
    // 3. 본인 QR 토큰 조회 (추가)
    // ==========================================
    @Transactional(readOnly = true)
    public String getMyQrToken(Long reservationId, Long userId) {

        ResourceReservation reservation =
                findMyReservation(reservationId, userId);

        if (!"APPROVED".equals(reservation.getStatus())) {
            throw new IllegalArgumentException(
                    "QR코드를 조회할 수 없는 예약 상태입니다.");
        }

        if (reservation.getCheckedInAt() != null) {
            throw new IllegalArgumentException(
                    "이미 체크인한 예약입니다.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );

        LocalDateTime now = LocalDateTime.now();

        if (!now.isBefore(startDateTime.plusMinutes(30))) {
            throw new IllegalArgumentException(
                    "QR코드 사용 가능 시간이 지났습니다.");
        }

        if (reservation.getQrToken() == null
                || reservation.getQrToken().isBlank()) {
            throw new IllegalArgumentException(
                    "발급된 QR코드가 없습니다.");
        }

        return reservation.getQrToken();
    }

    // ==========================================
    // 4. 공용 자원 이용 종료
    // ==========================================
    @Transactional
    public ResourceReservation completeReservation(
            Long reservationId,
            Long userId) {

        ResourceReservation reservation =
                findMyReservation(reservationId, userId);

        SharedResource resource =
                sharedResourceRepository.findById(
                                reservation.getResourceId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "공용 자원을 찾을 수 없습니다."));

        if (!"ITEM".equals(resource.getResourceType())
                && !"SPACE".equals(resource.getResourceType())) {

            throw new IllegalArgumentException(
                    "종료할 수 없는 공용 자원 유형입니다.");
        }

        if (reservation.getReturnedAt() != null
                || "COMPLETED".equals(reservation.getStatus())) {

            throw new IllegalArgumentException(
                    "이미 이용 종료된 예약입니다.");
        }

        if (!"APPROVED".equals(reservation.getStatus())
                && !("ITEM".equals(resource.getResourceType())
                && "OVERDUE".equals(reservation.getStatus()))) {

            throw new IllegalArgumentException(
                    "이용 종료할 수 없는 예약 상태입니다.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );

        if (LocalDateTime.now().isBefore(startDateTime)) {

            throw new IllegalArgumentException(
                    "예약 시작 전에는 이용 종료할 수 없습니다.");
        }

        reservation.setReturnedAt(LocalDateTime.now());
        reservation.setStatus("COMPLETED");

        return reservation;
    }

    // ==========================================
    // 5. 공용 자원 예약 신청
    // ==========================================
    @Transactional
    public ResourceReservation createReservation(
            ResourceReservationRequestDto dto) {

        if (dto == null
                || dto.getResourceId() == null
                || dto.getUserId() == null
                || dto.getReservationDate() == null
                || dto.getStartTime() == null
                || dto.getEndTime() == null) {

            throw new IllegalArgumentException(
                    "예약 정보를 모두 입력해야 합니다.");
        }

        SharedResource resource =
                sharedResourceRepository.findByIdForUpdate(
                                dto.getResourceId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "공용 자원을 찾을 수 없습니다."));

        if (!"AVAILABLE".equals(resource.getResourceStatus())) {

            throw new IllegalArgumentException(
                    "현재 예약할 수 없는 공용 자원입니다.");
        }

        long minutes = Duration.between(
                dto.getStartTime(),
                dto.getEndTime()
        ).toMinutes();

        if (minutes != 60 && minutes != 120) {

            throw new IllegalArgumentException(
                    "최초 예약은 1시간 또는 2시간만 가능합니다.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(
                dto.getReservationDate(),
                dto.getStartTime()
        );

        if (!startDateTime.isAfter(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "현재 시간 이후로만 예약할 수 있습니다.");
        }

        if ("ITEM".equals(resource.getResourceType())) {

            boolean hasOverdue = resourceReservationRepository
                    .existsByResourceIdAndReturnedAtIsNullAndStatusIn(
                            dto.getResourceId(),
                            List.of("OVERDUE")
                    );

            if (hasOverdue) {

                throw new IllegalArgumentException(
                        "미반납된 물품이므로 예약할 수 없습니다.");
            }
        }

        List<ResourceReservation> reservations =
                resourceReservationRepository
                        .findByResourceIdAndReservationDate(
                                dto.getResourceId(),
                                dto.getReservationDate()
                        );

        for (ResourceReservation existing : reservations) {

            if (!"APPROVED".equals(existing.getStatus())
                    && !"OVERDUE".equals(existing.getStatus())) {

                continue;
            }

            boolean isOverlapping =
                    dto.getStartTime().isBefore(existing.getEndTime())
                            && dto.getEndTime().isAfter(existing.getStartTime());

            if (isOverlapping) {

                throw new IllegalArgumentException(
                        "이미 예약된 시간입니다.");
            }
        }

        ResourceReservation reservation = new ResourceReservation();

        reservation.setReservationNumber(
                "RES-" + UUID.randomUUID().toString().substring(0, 8)
        );

        reservation.setResourceId(dto.getResourceId());
        reservation.setUserId(dto.getUserId());
        reservation.setReservationDate(dto.getReservationDate());
        reservation.setStartTime(dto.getStartTime());
        reservation.setEndTime(dto.getEndTime());

        reservation.setStatus("APPROVED");

        // 예약 승인 시 QR 토큰 자동 발급
        reservation.setQrToken(UUID.randomUUID().toString());

        ResourceReservation saved =
                resourceReservationRepository.save(reservation);

        eventPublisher.publishEvent(
                ReservationStatusChangedEvent.of(
                        saved,
                        resource,
                        "APPROVED"
                )
        );

        return saved;
    }

    // ==========================================
    // 6. 공용 자원 예약 취소
    // ==========================================
    @Transactional
    public ResourceReservation cancelReservation(
            Long reservationId,
            Long userId) {

        ResourceReservation reservation =
                findMyReservation(reservationId, userId);

        if ("CANCELED".equals(reservation.getStatus())) {

            throw new IllegalArgumentException(
                    "이미 취소된 예약입니다.");
        }

        if (!"APPROVED".equals(reservation.getStatus())) {

            throw new IllegalArgumentException(
                    "취소할 수 없는 예약 상태입니다.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );

        if (!LocalDateTime.now().isBefore(startDateTime)) {

            throw new IllegalArgumentException(
                    "예약 시작 후에는 취소할 수 없습니다.");
        }

        reservation.setStatus("CANCELED");

        sharedResourceRepository.findById(reservation.getResourceId())
                .ifPresent(resource -> eventPublisher.publishEvent(
                        ReservationStatusChangedEvent.of(
                                reservation,
                                resource,
                                "CANCELED"
                        )
                ));

        return reservation;
    }

    // ==========================================
    // 7. 관리자 전체 예약 조회
    // ==========================================
    @Transactional(readOnly = true)
    public List<ResourceReservation> getAllReservations() {

        return resourceReservationRepository
                .findAllByOrderByCreatedAtDesc();
    }

    // ==========================================
    // 8. 관리자 자원별 예약 조회
    // ==========================================
    @Transactional(readOnly = true)
    public List<ResourceReservation> getReservationsByResourceId(
            Long resourceId) {

        return resourceReservationRepository
                .findByResourceIdOrderByCreatedAtDesc(resourceId);
    }

    // ==========================================
    // 9. 관리자 예약 상태별 조회
    // ==========================================
    @Transactional(readOnly = true)
    public List<ResourceReservation> getReservationsByStatus(
            String status) {

        return resourceReservationRepository
                .findByStatusOrderByCreatedAtDesc(status);
    }

    // ==========================================
    // 10. 관리자 연체 이력 조회
    // ==========================================
    @Transactional(readOnly = true)
    public List<ResourceReservation> getOverdueReservations() {

        return resourceReservationRepository
                .findByIsOverdueTrueOrderByCreatedAtDesc();
    }

    // ==========================================
    // 11. QR 스캔 체크인 처리
    // ==========================================
    @Transactional
    public ResourceReservation checkIn(String qrToken) {

        if (qrToken == null || qrToken.isBlank()) {

            throw new IllegalArgumentException(
                    "QR 토큰이 없습니다.");
        }

        ResourceReservation reservation =
                resourceReservationRepository
                        .findByQrTokenForUpdate(qrToken)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "유효하지 않은 QR코드입니다."));

        if (!"APPROVED".equals(reservation.getStatus())) {

            throw new IllegalArgumentException(
                    "체크인할 수 없는 예약 상태입니다.");
        }

        if (reservation.getCheckedInAt() != null) {

            throw new IllegalArgumentException(
                    "이미 체크인한 예약입니다.");
        }

        SharedResource resource =
                sharedResourceRepository.findById(
                                reservation.getResourceId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "공용 자원을 찾을 수 없습니다."));

        if (!"SPACE".equals(resource.getResourceType())
                && !"ITEM".equals(resource.getResourceType())) {

            throw new IllegalArgumentException(
                    "체크인할 수 없는 공용 자원 유형입니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime startDateTime = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );

        LocalDateTime endDateTime = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getEndTime()
        );

        // 예약 시작 전 체크인 차단
        if (now.isBefore(startDateTime)) {

            throw new IllegalArgumentException(
                    "예약 시작 전에는 체크인할 수 없습니다.");
        }

        // 예약 종료 후 체크인 차단
        if (!now.isBefore(endDateTime)) {

            throw new IllegalArgumentException(
                    "예약 종료 후에는 체크인할 수 없습니다.");
        }

        // SPACE와 ITEM 모두 시작 후 30분 이내 체크인
        LocalDateTime checkInDeadline =
                startDateTime.plusMinutes(30);

        if (!now.isBefore(checkInDeadline)) {

            throw new IllegalArgumentException(
                    "체크인 가능 시간이 지났습니다.");
        }

        // QR 체크인 시간 저장
        reservation.setCheckedInAt(now);

        return reservation;
    }
}
