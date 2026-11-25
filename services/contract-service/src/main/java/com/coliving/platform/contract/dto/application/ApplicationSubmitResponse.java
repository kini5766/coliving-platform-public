package com.coliving.platform.contract.dto.application;

import com.coliving.platform.contract.enums.ContractStatus;

import java.time.OffsetDateTime;

/**
 * [1-2 신청서 제출] 응답
 * 프론트는 신청번호로 접수 완료 화면에 이동한다.
 *
 * @param status 항상 SUBMITTED
 * @author 한성현
 */
public record ApplicationSubmitResponse(
        String applicationNo,
        ContractStatus status,
        OffsetDateTime submittedAt
) {}
