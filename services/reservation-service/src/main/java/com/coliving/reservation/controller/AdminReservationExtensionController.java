package com.coliving.reservation.controller;

import com.coliving.reservation.entity.ReservationExtension;
import com.coliving.reservation.service.ReservationExtensionService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reservation-service/admin/reservation-extensions")
public class AdminReservationExtensionController {

    private final ReservationExtensionService reservationExtensionService;

    // 1. 관리자 예약 연장 기록 전체 조회
    @GetMapping
    public List<ReservationExtension> findAll() {
        return reservationExtensionService.findAll();
    }

    // 2. 관리자 예약 연장 기록 상세 조회
    @GetMapping("/{id}")
    public ReservationExtension findById(@PathVariable Long id) {
        return reservationExtensionService.findById(id);
    }

    // 3. 관리자 추가 연장 차단
    @PostMapping("/additional")
    public ReservationExtension additionalExtension() {

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "예약 연장은 최대 1회만 가능합니다."
        );
    }
}