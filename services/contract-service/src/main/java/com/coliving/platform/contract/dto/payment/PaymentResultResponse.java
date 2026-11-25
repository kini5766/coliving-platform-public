package com.coliving.platform.contract.dto.payment;

import com.coliving.platform.contract.enums.ContractStatus;

import java.time.OffsetDateTime;

/**
 * [3 결제 승인] 응답 — PG와 상관없이 같은 형태 (지금은 카카오페이만)
 *
 * 프론트는 이 응답을 받으면 계약 완료 화면으로 이동하고,
 * 완료 화면은 ApplicationDetailResponse로 다시 조회한다.
 *
 * @param status 항상 PAID
 * @param method 결제 수단 ("KAKAOPAY")
 * @author 한성현
 */
public record PaymentResultResponse(
        ContractStatus status,
        String method,
        Long paidAmount,
        OffsetDateTime paidAt
) {}
