package com.coliving.platform.security.http;

import com.coliving.platform.common.config.AppProperties;
import com.coliving.platform.security.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 브라우저 세션 쿠키.
 * 이름은 설정값이고 값은 세션 번호다. 토큰은 쿠키에 넣지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-27
 */
@Component
@RequiredArgsConstructor
public class SessionCookieFactory {

    private final AppProperties appProperties;
    private final AuthProperties authProperties;

    /** 세션 쿠키를 쓴다. 수명은 리프레시 토큰 만료와 같다. */
    public ResponseCookie write(String sessionId) {
        return base(sessionId)
                .maxAge(authProperties.refreshTokenExpiration())
                .build();
    }

    /** 쿠키를 즉시 만료시킨다. */
    public ResponseCookie clear() {
        return base("")
                .maxAge(0)
                .build();
    }

    /** 쿠키에서 세션 번호를 읽는다. */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = appProperties.cookie().sessionCookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** HttpOnly 세션 쿠키. Secure 와 SameSite 는 설정을 따른다. */
    private ResponseCookie.ResponseCookieBuilder base(String value) {
        // sessionCookieName is the configured cookie name. The value is a session id.
        return ResponseCookie.from(appProperties.cookie().sessionCookieName(), value)
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .path("/")
                .sameSite(appProperties.cookie().sameSite());
    }
}
