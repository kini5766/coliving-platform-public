package com.coliving.platform.security.http;

import com.coliving.platform.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 소셜 로그인 진행용 HttpOnly 쿠키.
 * 값은 Redis 트랜잭션 번호이고, SameSite 는 세션 쿠키와 달리 항상 Lax 다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@Component
@RequiredArgsConstructor
public class OAuthTransactionCookie {

    private final AppProperties appProperties;

    /** 트랜잭션 번호 쿠키를 쓴다. 수명은 oauthTransactionMaxAge 다. */
    public ResponseCookie write(String transactionId) {
        return base(transactionId)
                .maxAge(appProperties.cookie().oauthTransactionMaxAge())
                .build();
    }

    /** 쿠키를 즉시 만료시킨다. */
    public ResponseCookie clear() {
        return base("")
                .maxAge(0)
                .build();
    }

    /** 쿠키에서 트랜잭션 번호를 읽는다. */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = appProperties.cookie().oauthTransactionName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** HttpOnly, Secure, path /, SameSite=Lax 쿠키를 만든다. */
    private ResponseCookie.ResponseCookieBuilder base(String value) {
        // The provider returns with a cross-site top-level GET, so this cookie stays Lax
        // even when the session cookie is Strict.
        return ResponseCookie.from(appProperties.cookie().oauthTransactionName(), value)
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .path("/")
                .sameSite("Lax");
    }
}
