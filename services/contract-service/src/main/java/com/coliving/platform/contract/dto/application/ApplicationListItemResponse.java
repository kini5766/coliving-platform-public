package com.coliving.platform.contract.dto.application;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.enums.ContractStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * [나의 신청 목록] GET /contract-service/apply/applications
 *
 * 목록 한 줄. 상세 정보(계약서 포함)는 ApplicationDetailResponse·ContractDocumentResponse로 따로 조회한다.
 * 작성 중(DRAFT)은 제출 전 임시 상태라 목록에 넣지 않는다. 최근 신청 순.
 *
 * @param moveOutDate 퇴실 예정일 (입주일 + 개월 - 1일)
 * @param submittedAt 제출 시각 (= 금액 고정 시각)
 * @author 한성현
 */
public record ApplicationListItemResponse(
        String applicationNo,
        ContractStatus status,
        String buildingName,
        String roomName,
        String roomType,
        LocalDate moveInDate,
        LocalDate moveOutDate,
        Integer months,
        OffsetDateTime submittedAt
) {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public static ApplicationListItemResponse from(Contract c) {
        return new ApplicationListItemResponse(
                c.getApplicationNo(),
                c.getStatus(),
                c.getBuildingName(),
                c.getRoomName(),
                c.getRoomType(),
                c.getMoveInDate(),
                c.getMoveOutDate(),
                c.getContractMonths() == null ? null : c.getContractMonths().intValue(),
                c.getAmountFixedAt() == null ? null : c.getAmountFixedAt().atOffset(KST)
        );
    }
}
