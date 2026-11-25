
package com.coliving.reservation.service;

import com.coliving.reservation.entity.SharedResource;
import com.coliving.reservation.repository.SharedResourceRepository;
import com.coliving.reservation.repository.ResourceReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SharedResourceService {

    private final SharedResourceRepository sharedResourceRepository;
    private final ResourceReservationRepository resourceReservationRepository;

    // 1. 건물별 공유 자원 목록 조회
    public List<SharedResource> findByBuildingId(Long buildingId) {

        return sharedResourceRepository.findByBuildingId(buildingId);
    }

    // 2. 공유 자원 등록
    public SharedResource createResource(SharedResource sharedResource) {

        // 공용 자원 상태값 검증
        String status = sharedResource.getResourceStatus();

        if (status == null
                || (!status.equals("AVAILABLE")
                && !status.equals("UNAVAILABLE"))) {

            throw new IllegalArgumentException(
                    "공용 자원 상태는 AVAILABLE 또는 UNAVAILABLE만 가능합니다.");
        }

        // 공용 자원 유형 검증
        String type = sharedResource.getResourceType();

        if (type == null
                || (!type.equals("SPACE")
                && !type.equals("ITEM"))) {

            throw new IllegalArgumentException(
                    "공용 자원 유형은 SPACE 또는 ITEM만 가능합니다.");
        }

        // 검증을 통과한 공용 자원 저장
        return sharedResourceRepository.save(sharedResource);
    }

    // 3. 공유 자원 상세 조회
    public SharedResource findById(Long id) {

        return sharedResourceRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공유 자원을 찾을 수 없습니다."));
    }

    // 4. 공용 자원 수정
    public SharedResource updateResource(
            Long id,
            SharedResource updatedResource) {

        SharedResource sharedResource = sharedResourceRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공유 자원을 찾을 수 없습니다."));

        // 공용 자원 이름 수정
        if (updatedResource.getResourceName() != null) {
            sharedResource.setResourceName(
                    updatedResource.getResourceName());
        }

        // 공용 자원 유형 수정 및 검증
        if (updatedResource.getResourceType() != null) {

            String type = updatedResource.getResourceType();

            if (!type.equals("SPACE")
                    && !type.equals("ITEM")) {

                throw new IllegalArgumentException(
                        "공용 자원 유형은 SPACE 또는 ITEM만 가능합니다.");
            }

            sharedResource.setResourceType(type);
        }

        // 공용 자원 설명 수정
        if (updatedResource.getDescription() != null) {
            sharedResource.setDescription(
                    updatedResource.getDescription());
        }

        // 공용 자원 상태 수정 및 검증
        if (updatedResource.getResourceStatus() != null) {

            String status = updatedResource.getResourceStatus();

            if (!status.equals("AVAILABLE")
                    && !status.equals("UNAVAILABLE")) {

                throw new IllegalArgumentException(
                        "공용 자원 상태는 AVAILABLE 또는 UNAVAILABLE만 가능합니다.");
            }

            sharedResource.setResourceStatus(status);
        }

        // 최대 이용 시간 수정
        if (updatedResource.getMaxUsageHours() != null) {
            sharedResource.setMaxUsageHours(
                    updatedResource.getMaxUsageHours());
        }

        // 건물 ID 수정
        if (updatedResource.getBuildingId() != null) {
            sharedResource.setBuildingId(
                    updatedResource.getBuildingId());
        }

        // 이미지 URL 수정
        if (updatedResource.getImageUrl() != null) {
            sharedResource.setImageUrl(
                    updatedResource.getImageUrl());
        }

        // 건물 내부 위치 수정
        if (updatedResource.getLocation() != null) {
            sharedResource.setLocation(
                    updatedResource.getLocation());
        }

        // 최대 이용 인원 수정
        if (updatedResource.getCapacity() != null) {
            sharedResource.setCapacity(
                    updatedResource.getCapacity());
        }

        // 운영 시작 시간 수정
        if (updatedResource.getOpeningTime() != null) {
            sharedResource.setOpeningTime(
                    updatedResource.getOpeningTime());
        }

        // 운영 종료 시간 수정
        if (updatedResource.getClosingTime() != null) {
            sharedResource.setClosingTime(
                    updatedResource.getClosingTime());
        }

        // 변경된 공용 자원 저장
        return sharedResourceRepository.save(sharedResource);
    }

    // 5. 관리자 공용 자원 삭제
    @Transactional
    public void deleteResource(Long id) {

        // 삭제할 공용 자원 조회
        SharedResource sharedResource = sharedResourceRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "공용 자원을 찾을 수 없습니다."));

        // 기존 예약 기록이 있는지 확인
        if (!resourceReservationRepository
                .findByResourceIdOrderByCreatedAtDesc(id).isEmpty()) {

            throw new IllegalStateException(
                    "예약 기록이 있는 공용 자원은 삭제할 수 없습니다.");
        }

        // 예약 기록이 없는 공용 자원만 삭제
        sharedResourceRepository.delete(sharedResource);
    }
}
