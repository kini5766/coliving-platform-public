package com.coliving.platform.security.controller;

import com.coliving.platform.security.dto.AccessTokenResponse;
import com.coliving.platform.security.dto.SessionResponseDTO;
import com.coliving.platform.security.http.SessionCookieFactory;
import com.coliving.platform.security.service.AuthService;
import com.coliving.platform.user.entity.Role;
import com.coliving.platform.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Set;

/**
 * 세션 확인, 로그아웃, 게이트웨이용 액세스 토큰 조회.
 * 로그인 본문은 LoginFilter 가 받는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionCookieFactory sessionCookieFactory;
    private final UserRepository userRepository;

    /** 세션 쿠키가 있으면 로그인 여부, 회원 번호, 닉네임을 돌려준다. 없으면 401. */
    @GetMapping("/user/session")
    public ResponseEntity<SessionResponseDTO> session(HttpServletRequest request) {
        return sessionCookieFactory.read(request)
                .flatMap(authService::sessionSubject)
                .flatMap(this::sessionResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /** 세션의 회원 번호로 닉네임을 찾아 응답을 만든다. 번호가 아니면 비운다. */
    private Optional<SessionResponseDTO> sessionResponse(String userId) {
        long id;
        try {
            id = Long.parseLong(userId);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        return userRepository.findById(id)
                .map(user -> new SessionResponseDTO(true, userId, blankToNull(user.getNickname())));
    }

    /** 관리자 세션 확인. 쿠키가 없으면 401, ADMIN 이 아니면 403, 맞으면 본문 없이 204. */
    @GetMapping("/admin/session")
    public ResponseEntity<Void> adminSession(HttpServletRequest request) {
        Optional<Set<Role>> roles = sessionCookieFactory.read(request)
                .flatMap(authService::sessionSubject)
                .flatMap(this::accountRoles);
        if (roles.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!roles.get().contains(Role.ADMIN)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.noContent().build();
    }

    /** 세션 주체의 역할. 번호가 아니거나 회원이 없으면 비운다. */
    private Optional<Set<Role>> accountRoles(String userId) {
        long id;
        try {
            id = Long.parseLong(userId);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        return userRepository.findById(id).map(user -> user.getRoles() == null ? Set.of() : user.getRoles());
    }

    /** 빈 닉네임은 JSON 에서 빠지도록 null 로 바꾼다. */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /** 현재 세션만 지우고 세션 쿠키를 만료시킨다. 다른 기기의 세션은 남긴다. */
    @PostMapping("/user/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionCookieFactory.read(request).ifPresent(authService::closeSession);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieFactory.clear().toString());
        return ResponseEntity.noContent().build();
    }

    /** 게이트웨이가 세션 쿠키를 액세스 토큰으로 바꿀 때 호출한다. 게이트웨이 라우트는 아니다. */
    @GetMapping("/internal/session/access")
    public ResponseEntity<AccessTokenResponse> accessToken(HttpServletRequest request) {
        return sessionCookieFactory.read(request)
                .flatMap(authService::currentAccessToken)
                .map(token -> ResponseEntity.ok(new AccessTokenResponse(token)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
