package com.coliving.platform.contract.dummy;

import com.coliving.platform.common.exception.IdentityServiceUnavailableException;
import com.coliving.platform.common.exception.IdentityVerificationException;
import com.coliving.platform.contract.dto.internal.VerifiedIdentity;
import com.coliving.platform.contract.service.IdentityVerificationClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * [개발용 더미] 포트원 없이 본인인증 결과를 흉내낸다. "dummy-portone" 프로필에서만 등록된다.
 * 결과는 identityVerificationId에 들어간 글자로 고른다 (프론트에서 ID를 바꿔 테스트)
 *   "...fail..."     : FAILED (인증 실패)
 *   "...ready..."    : READY (창은 열었는데 인증 안 끝남)
 *   "...other..."    : VERIFIED지만 다른 사람 (이름 불일치)
 *   "...notfound..." : 포트원에 없는 ID → 400
 *   "...down..."     : 포트원 장애 → 503
 *   그 외             : VERIFIED, 더미 회원 1번(홍길동, 010-1234-5678)
 * ⚠ 운영 설정에 dummy-portone을 넣지 말 것.
 *
 * @author 한성현
 */
@Service
@Profile("dummy-portone")
public class DummyIdentityVerificationClient implements IdentityVerificationClient {

    @Override
    public VerifiedIdentity fetch(String id) {
        if (id.contains("down")) throw new IdentityServiceUnavailableException("[더미] 포트원 장애", null);
        if (id.contains("notfound")) throw new IdentityVerificationException("[더미] 본인인증 내역을 찾을 수 없습니다.");
        if (id.contains("fail")) return new VerifiedIdentity(id, "FAILED", null, null, null);
        if (id.contains("ready")) return new VerifiedIdentity(id, "READY", null, null, null);
        if (id.contains("other")) return new VerifiedIdentity(id, "VERIFIED", "김다른", "01099998888", Instant.now());
        return new VerifiedIdentity(id, "VERIFIED", "홍길동", "01012345678", Instant.now());
    }
}
