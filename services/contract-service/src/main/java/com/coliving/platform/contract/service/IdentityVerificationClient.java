package com.coliving.platform.contract.service;

import com.coliving.platform.contract.dto.internal.VerifiedIdentity;

/**
 * 본인인증 결과 조회 창구.
 * - 구현: PortOneIdentityVerificationClient (포트원 REST API)
 *
 * @author 한성현
 */
public interface IdentityVerificationClient {

    VerifiedIdentity fetch(String identityVerificationId);
}
