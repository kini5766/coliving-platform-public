
package com.coliving.reservation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "resource_reservations")
@Getter
@Setter
public class ResourceReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_number", nullable = false, unique = true, length = 30)
    private String reservationNumber;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "REQUESTED";

    // 실제 이용 종료 또는 물품 반납 시간
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    // 연체 이력 여부
    @Column(name = "is_overdue", nullable = false)
    private Boolean isOverdue = false;

    // ==========================================
    // QR 체크인 관련 필드
    // ==========================================

    // 예약별 고유 QR 토큰
    // 일반 예약 JSON 응답에서는 노출하지 않음
    @JsonIgnore
    @Column(name = "qr_token", unique = true, length = 100)
    private String qrToken;

    // QR코드 스캔으로 체크인한 시간
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    // ==========================================
    // 생성 및 수정 시간
    // ==========================================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
