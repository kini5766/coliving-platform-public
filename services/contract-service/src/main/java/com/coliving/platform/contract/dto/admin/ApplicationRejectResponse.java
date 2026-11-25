package com.coliving.platform.contract.dto.admin;

import com.coliving.platform.contract.enums.ContractStatus;

import java.time.OffsetDateTime;

/**
 * [반려 - 관리자] POST /contract-service/admin/applications/{applicationId}/reject 응답
 *
 * @param status 항상 REJECTED
 * @author 한성현
 */
public record ApplicationRejectResponse(
        String applicationNo,
        ContractStatus status,
        String rejectReason,
        OffsetDateTime rejectedAt
) {}
