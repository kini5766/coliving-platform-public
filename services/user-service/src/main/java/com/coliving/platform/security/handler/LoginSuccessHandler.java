package com.coliving.platform.security.handler;

import com.coliving.platform.security.dto.SessionResponseDTO;
import com.coliving.platform.security.http.SessionCookieFactory;
import com.coliving.platform.security.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 이메일 로그인 성공. 세션 쿠키를 심고 토큰 없는 JSON 을 돌려준다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final SessionCookieFactory sessionCookieFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 세션을 열고 Set-Cookie 와 SessionResponseDTO 를 쓴다. */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        // authentication.getName() => CustomUserDetails 의 getName 과 동일한 값
        String sub = authentication.getName();
        String sessionId = authService.openSession(sub);

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieFactory.write(sessionId).toString());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new SessionResponseDTO(true, sub, null));
    }
}
