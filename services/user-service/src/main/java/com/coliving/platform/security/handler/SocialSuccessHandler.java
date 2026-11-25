package com.coliving.platform.security.handler;

import com.coliving.platform.common.config.AppProperties;
import com.coliving.platform.security.http.SessionCookieFactory;
import com.coliving.platform.security.oauth.SocialReturnPath;
import com.coliving.platform.security.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인 성공. 같은 세션 쿠키를 심고 user-web 의 return_to 로 보낸다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@Component
@RequiredArgsConstructor
public class SocialSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final AppProperties appProperties;
    private final SessionCookieFactory sessionCookieFactory;

    /** 세션 쿠키를 심고 안전한 return_to 로 리다이렉트한다. */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // authentication.getName() => CustomOAuth2User 의 getName 과 동일한 값
        String sub = authentication.getName();
        String sessionId = authService.openSession(sub);

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieFactory.write(sessionId).toString());
        response.sendRedirect(SocialReturnPath.redirect(appProperties.frontend().url(), request));
    }
}
