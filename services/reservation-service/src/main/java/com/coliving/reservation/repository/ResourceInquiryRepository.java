package com.coliving.reservation.repository;

import com.coliving.reservation.entity.ResourceInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceInquiryRepository extends JpaRepository<ResourceInquiry, Long> {

    // 특정 사용자가 등록한 문의 목록 조회
    List<ResourceInquiry> findByUserId(Long userId);

    // 특정 예약에 등록된 문의 목록 조회
    List<ResourceInquiry> findByReservationId(Long reservationId);
}