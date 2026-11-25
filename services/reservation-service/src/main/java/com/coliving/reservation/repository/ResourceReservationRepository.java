
package com.coliving.reservation.repository;

import com.coliving.reservation.entity.ResourceReservation;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ResourceReservationRepository
        extends JpaRepository<ResourceReservation, Long> {

    // 1. 동일 자원, 동일 날짜 예약 조회
    List<ResourceReservation> findByResourceIdAndReservationDate(
            Long resourceId,
            LocalDate reservationDate
    );

    // 2. 예약 상태별 조회
    List<ResourceReservation> findByStatus(String status);

    // 3. 미반납 연체 물품 존재 여부 확인
    boolean existsByResourceIdAndReturnedAtIsNullAndStatusIn(
            Long resourceId,
            List<String> statuses
    );

    // 4. 예약 ID로 조회하면서 DB 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResourceReservation r WHERE r.id = :id")
    Optional<ResourceReservation> findByIdForUpdate(
            @Param("id") Long id
    );

    // 5. 사용자별 예약 조회
    List<ResourceReservation> findByUserId(Long userId);

    // 6. 사용자 본인 예약 조회
    Optional<ResourceReservation> findByIdAndUserId(
            Long id,
            Long userId
    );

    // 7. 관리자 전체 예약 조회
    List<ResourceReservation> findAllByOrderByCreatedAtDesc();

    // 8. 자원별 예약 조회
    List<ResourceReservation> findByResourceIdOrderByCreatedAtDesc(
            Long resourceId
    );

    // 9. 예약 상태별 조회 (최신순)
    List<ResourceReservation> findByStatusOrderByCreatedAtDesc(
            String status
    );

    // 10. 연체 이력 조회
    List<ResourceReservation> findByIsOverdueTrueOrderByCreatedAtDesc();

    // 11. QR 토큰으로 예약 조회 및 DB 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResourceReservation r WHERE r.qrToken = :qrToken")
    Optional<ResourceReservation> findByQrTokenForUpdate(
            @Param("qrToken") String qrToken
    );
}
