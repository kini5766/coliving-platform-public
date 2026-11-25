package com.coliving.platform.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * auth.* 설정.
 * 액세스 토큰 서명 키, 발급자, 만료, 리프레시 만료, 갱신 여유 시간을 묶는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String accessTokenPublic,
        String accessTokenPrivate,
        String accessTokenKeyId,
        String accessTokenIssuer,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        Duration accessTokenRefreshSkew
) {
}