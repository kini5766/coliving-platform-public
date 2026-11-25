package com.coliving.platform.security.session;

/**
 * Redis 에 두는 브라우저 세션.
 * sub 는 회원 번호이고, 토큰은 서버에만 있다. 쿠키에는 세션 번호만 나간다.
 *
 * @author 조휘재
 * 작성일: 2026-10-27
 */
public record BrowserSession(
        String sub,
        String refreshToken,
        String accessToken,
        long accessExpiresAtEpochMilli
) {
}
