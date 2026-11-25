package com.coliving.platform.security.http;

import com.coliving.platform.security.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 요청의 세션 쿠키에서 회원 번호를 읽는다. 쿠키가 없거나 번호가 아니면 비운다.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
@Component
@RequiredArgsConstructor
public class CurrentSessionUser {

    private final SessionCookieFactory sessionCookieFactory;
    private final AuthService authService;

    /** 세션 주체를 Long 회원 번호로 바꾼다. */
    public Optional<Long> id(HttpServletRequest request) {
        return sessionCookieFactory.read(request)
                .flatMap(authService::sessionSubject)
                .flatMap(CurrentSessionUser::parse);
    }

    /** 숫자 문자열이 아니면 비운다. */
    private static Optional<Long> parse(String userId) {
        try {
            return Optional.of(Long.parseLong(userId));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
