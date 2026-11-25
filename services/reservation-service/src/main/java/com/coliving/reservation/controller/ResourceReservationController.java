
package com.coliving.reservation.controller;

import com.coliving.reservation.dto.ResourceReservationRequestDto;
import com.coliving.reservation.entity.ResourceReservation;
import com.coliving.reservation.service.ResourceReservationService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reservation-service/reservations")
public class ResourceReservationController {

    private final ResourceReservationService resourceReservationService;

    // ==========================================
    // 1. 공용 자원 예약 신청
    // ==========================================
    @PostMapping
    public ResourceReservation createReservation(
            @RequestHeader("X-User") Long userId,
            @RequestBody ResourceReservationRequestDto dto) {

        dto.setUserId(userId);

        return resourceReservationService.createReservation(dto);
    }

    // ==========================================
    // 2. 공용 자원 이용 종료
    // ==========================================
    @PatchMapping("/{id}/return")
    public ResourceReservation completeReservation(
            @PathVariable Long id,
            @RequestHeader("X-User") Long userId) {

        return resourceReservationService.completeReservation(id, userId);
    }

    // ==========================================
    // 3. 공용 자원 예약 취소
    // ==========================================
    @PatchMapping("/{id}/cancel")
    public ResourceReservation cancelReservation(
            @PathVariable Long id,
            @RequestHeader("X-User") Long userId) {

        return resourceReservationService.cancelReservation(id, userId);
    }

    // ==========================================
    // 4. 내 공용 자원 예약 목록 조회
    // ==========================================
    @GetMapping("/my")
    public List<ResourceReservation> getMyReservations(
            @RequestHeader("X-User") Long userId) {

        return resourceReservationService.getMyReservations(userId);
    }

    // ==========================================
    // 5. 본인 예약 QR 토큰 조회 (추가)
    // ==========================================
    @GetMapping("/{id}/qr-token")
    public Map<String, String> getMyQrToken(
            @PathVariable Long id,
            @RequestHeader("X-User") Long userId) {

        String qrToken =
                resourceReservationService.getMyQrToken(id, userId);

        return Map.of("qrToken", qrToken);
    }

    // ==========================================
    // 6. 관리자 전체 예약 조회
    // ==========================================
    @GetMapping("/admin")
    public List<ResourceReservation> getAllReservations() {

        return resourceReservationService.getAllReservations();
    }

    // ==========================================
    // 7. 관리자 자원별 예약 조회
    // ==========================================
    @GetMapping("/admin/resource/{resourceId}")
    public List<ResourceReservation> getReservationsByResourceId(
            @PathVariable Long resourceId) {

        return resourceReservationService
                .getReservationsByResourceId(resourceId);
    }

    // ==========================================
    // 8. 관리자 예약 상태별 조회
    // ==========================================
    @GetMapping("/admin/status/{status}")
    public List<ResourceReservation> getReservationsByStatus(
            @PathVariable String status) {

        return resourceReservationService
                .getReservationsByStatus(status);
    }

    // ==========================================
    // 9. 관리자 연체 이력 조회
    // ==========================================
    @GetMapping("/admin/overdue")
    public List<ResourceReservation> getOverdueReservations() {

        return resourceReservationService.getOverdueReservations();
    }

    // ==========================================
    // 10. QR 스캔 체크인 처리
    // ==========================================
    @PostMapping("/check-in")
    public ResourceReservation checkIn(
            @RequestBody Map<String, String> request) {

        String qrToken = request.get("qrToken");

        return resourceReservationService.checkIn(qrToken);
    }
}
