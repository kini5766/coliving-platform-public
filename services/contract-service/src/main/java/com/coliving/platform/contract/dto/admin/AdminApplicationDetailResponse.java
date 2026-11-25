package com.coliving.platform.contract.dto.admin;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.enums.ContractStatus;

import java.time.LocalDate;

/**
 * [관리자 계약서 상세] GET /contract-service/admin/applications/{applicationId}
 *
 * 신청자·호실·금액은 신청 당시 스냅샷이다. 제출 전(DRAFT)이면 신청자 값과 계약서 값은 null.
 *
 * @param applicationId  contract_application.id (관리자 화면 경로에 쓰는 번호)
 * @param documentId     계약서 ID (contract_document.id). 신청서 제출 때 만들어진다
 * @param userId         신청한 회원 ID. admin-web은 이 값을 memberId로 바꿔 쓴다 (contractApi.js)
 * @param moveOutDate    퇴실 예정일 (입주일 + 개월 - 1일, 당일까지 사용)
 * @param discountAmount 할인 금액. 할인 없음(10/27 결정) → 항상 0
 * @param firstPayment   첫 결제 금액 = 첫 달 이용료 + 보증금 (관리비 제외)
 * @param statusReason   REJECTED면 반려 사유, 그 외 null (보완 요청 없음: 반려 후 새 신청으로 처음부터)
 * @param content        계약서 본문 (HTML). 계약일은 결제 완료일로 채우고, 결제 전이면 빈칸
 * @author 한성현
 */
public record AdminApplicationDetailResponse(
        Long applicationId,
        Long documentId,
        String applicationNo,
        ContractStatus status,
        Long userId,
        String applicantName,
        String applicantEmail,
        String applicantPhone,
        String applicantAddress,
        String buildingName,
        String roomName,
        LocalDate moveInDate,
        LocalDate moveOutDate,
        int contractMonths,
        long monthlyFee,
        long deposit,
        long discountAmount,
        long firstPayment,
        String statusReason,
        String content
) {
    /**
     * @param document 계약서. 아직 없으면 null
     * @param content  보여줄 계약서 본문 (계약일까지 채운 값, ContractContentView). 계약서가 없으면 null
     */
    public static AdminApplicationDetailResponse of(Contract c, ContractDocument document, String content) {
        return new AdminApplicationDetailResponse(
                c.getId(),
                document == null ? null : document.getId(),
                c.getApplicationNo(),
                c.getStatus(),
                c.getUserId(),
                c.getApplicantName(),
                c.getApplicantEmail(),
                c.getApplicantPhone(),
                c.getApplicantAddress(),
                c.getBuildingName(),
                c.getRoomName(),
                c.getMoveInDate(),
                c.getMoveOutDate(),
                c.getContractMonths() == null ? 0 : c.getContractMonths(),
                c.getMonthlyFee(),
                c.getDeposit(),
                0L,
                c.getFirstPayment(),
                c.getStatus() == ContractStatus.REJECTED ? c.getRejectReason() : null,
                content
        );
    }
}
