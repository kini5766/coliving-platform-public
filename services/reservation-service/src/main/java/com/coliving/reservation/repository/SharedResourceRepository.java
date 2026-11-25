package com.coliving.reservation.repository;

import com.coliving.reservation.entity.SharedResource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SharedResourceRepository
        extends JpaRepository<SharedResource, Long> {

    // 건물별 공용 자원 목록 조회
    List<SharedResource> findByBuildingId(Long buildingId);

    // 예약 중복 방지를 위한 공용 자원 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SharedResource r WHERE r.id = :id")
    Optional<SharedResource> findByIdForUpdate(@Param("id") Long id);
}