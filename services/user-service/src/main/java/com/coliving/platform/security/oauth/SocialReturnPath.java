package com.coliving.platform.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 소셜 로그인 뒤 돌아갈 user-web 경로.
 * 앱 안 경로만 허용하고, 스키마나 프로토콜 상대 경로는 버린다.
 *
 * @author 조휘재
 * 작성일: 2026-10-30
 */
public final class SocialReturnPath {

    static final String ATTRIBUTE = "return_to";
    private static final int MAX_LENGTH = 2048;

    /** 유틸리티 클래스. 인스턴스를 만들지 않는다. */
    private SocialReturnPath() {
    }

    /**
     * Keeps an in-app path. The success redirect is the configured frontend origin
     * plus this value, so a scheme, a protocol-relative path, or a non-path value
     * would leave the app.
     */
    public static String sanitize(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7f || ch == '\\') {
                return null;
            }
        }
        if (!value.startsWith("/") || value.startsWith("//") || value.startsWith("/login")) {
            return null;
        }
        int query = value.indexOf('?');
        String path = query >= 0 ? value.substring(0, query) : value;
        if (path.contains("://")) {
            return null;
        }
        return value;
    }

    /** 인가 요청에 실린 return_to 를 콜백 요청 속성으로 옮긴다. */
    public static void remember(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        Object value = authorizationRequest.getAttribute(ATTRIBUTE);
        if (value instanceof String path) {
            String safe = sanitize(path);
            if (safe != null) {
                request.setAttribute(ATTRIBUTE, safe);
            }
        }
    }

    /** 프론트 주소에 안전한 경로를 붙인다. 없으면 프론트 주소만 돌려준다. */
    public static String redirect(String frontendUrl, HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        String path = value instanceof String text ? sanitize(text) : null;
        if (path == null) {
            return frontendUrl;
        }
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        return base + path;
    }
}
