package com.coliving.platform.contract.dto.admin;

import com.coliving.platform.contract.enums.ContractStatus;

import java.time.OffsetDateTime;

/**
 * [승인 - 관리자] POST /contract-service/admin/applications/{applicationId}/approve 응답
 * 승인은 상태만 바꾼다. 계약서(contract_document)는 신청서 제출 때 이미 만들어져 있다.
 *
 * @param status          항상 APPROVED
 * @param documentId      계약서 ID (contract_document.id)
 * @param contentHash     계약서 본문 SHA-256 (확인용)
 * @param paymentDeadline 결제 기한 (승인 + 1일)
 * @author 한성현
 */
public record ApplicationApproveResponse(
        String applicationNo,
        ContractStatus status,
        Long documentId,
        String contentHash,
        OffsetDateTime paymentDeadline
) {}
