package com.coliving.platform.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * app.* 설정. 프론트 주소와 세션·소셜 쿠키를 묶는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Frontend frontend,
        Cookie cookie
) {
    /**
     * 소셜 로그인 성공·실패 후 돌아갈 user-web 주소.
     *
     * @author 조휘재
     * 작성일: 2026-10-16
     */
    public record Frontend(
            String url
    ) {}

    /**
     * sessionCookieName is the HttpOnly session cookie name.
     * The cookie value is a session id. Access and refresh tokens stay on the server.
     * oauthTransactionName is the short-lived cookie used by every social provider
     * while the browser is at the provider. Its value is a Redis key id.
     *
     * @author 조휘재
     * 작성일: 2026-10-16
     */
    public record Cookie(
            boolean secure,
            String sameSite,
            String sessionCookieName,
            @DefaultValue("oauth_tx") String oauthTransactionName,
            @DefaultValue("10m") Duration oauthTransactionMaxAge
    ) {}
}