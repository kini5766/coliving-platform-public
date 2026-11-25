package com.coliving.platform.security.handler;

import com.coliving.platform.common.config.AppProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 소셜 로그인 실패. 오류 화면 없이 user-web 첫 주소로 보낸다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@Component
@RequiredArgsConstructor
public class SocialFailureHandler implements AuthenticationFailureHandler {

    private final AppProperties appProperties;

    /** app.frontend.url 로 리다이렉트한다. */
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        response.sendRedirect(appProperties.frontend().url());
    }
}
