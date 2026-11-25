package com.coliving.platform.security.dto;

/**
 * 게이트웨이 내부 조회용 액세스 토큰. 브라우저 로그인 응답에는 넣지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-27
 */
public record AccessTokenResponse(
        String accessToken
) {
}
