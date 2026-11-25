package com.coliving.platform.contract.dto.admin;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.enums.ContractStatus;

import java.time.LocalDate;

/**
 * [관리자 계약서 목록] GET /contract-service/admin/applications 의 항목 1개
 *
 * 상태 묶음·배지 글자는 관리자 화면이 붙이므로 여기서는 상태 원래 값만 준다.
 *
 * @param applicationId contract_application.id (관리자 화면 경로에 쓰는 번호)
 * @param applicationNo 화면 표시용 신청번호 (예: CT-20261021-0042)
 * @param applicantName 신청자 이름. 제출 전(DRAFT)이면 null
 * @param moveOutDate   퇴실 예정일 (당일까지 사용)
 * @param status        JSON에는 "SUBMITTED"처럼 enum 이름 문자열로 나간다
 * @author 한성현
 */
public record AdminApplicationListItemResponse(
        Long applicationId,
        String applicationNo,
        String applicantName,
        String buildingName,
        String roomName,
        LocalDate moveInDate,
        LocalDate moveOutDate,
        long monthlyFee,
        long deposit,
        ContractStatus status
) {
    public static AdminApplicationListItemResponse from(Contract c) {
        return new AdminApplicationListItemResponse(
                c.getId(),
                c.getApplicationNo(),
                c.getApplicantName(),
                c.getBuildingName(),
                c.getRoomName(),
                c.getMoveInDate(),
                c.getMoveOutDate(),
                c.getMonthlyFee(),
                c.getDeposit(),
                c.getStatus()
        );
    }
}
