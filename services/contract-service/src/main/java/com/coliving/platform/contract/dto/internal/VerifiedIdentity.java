package com.coliving.platform.contract.dto.internal;

import java.time.Instant;

/**
 * 포트원 본인인증 조회 결과 중 계약 서비스가 쓰는 값만 옮긴 DTO.
 * CI·DI·생년월일 등은 일부러 담지 않는다 (필요 없는 개인정보는 받지도 저장하지도 않는다).
 *
 * @param status READY / VERIFIED / FAILED. VERIFIED가 아니면 name·verifiedAt은 null
 * @param phone  숫자만 (예: "01012345678"). PG에 따라 없을 수 있어 null (다날은 별도 계약 필요)
 * @author 한성현
 */
public record VerifiedIdentity(
        String identityVerificationId,
        String status,
        String name,
        String phone,
        Instant verifiedAt
) {
    public boolean verified() {
        return "VERIFIED".equals(status);
    }
}
