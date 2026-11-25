package com.coliving.platform.user.controller;

import com.coliving.platform.security.http.CurrentSessionUser;
import com.coliving.platform.security.http.SessionCookieFactory;
import com.coliving.platform.user.dto.NicknamePayload;
import com.coliving.platform.user.repository.UserRepository;
import com.coliving.platform.user.service.AccountWithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 회원의 닉네임 변경과 탈퇴.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final CurrentSessionUser currentSessionUser;
    private final UserRepository userRepository;
    private final AccountWithdrawalService accountWithdrawalService;
    private final SessionCookieFactory sessionCookieFactory;

    private static final int NICKNAME_MAX = 100;

    /** 닉네임을 바꾼다. 빈 값은 지우고, 100자를 넘기면 400. 세션이 없으면 401. */
    @PutMapping("/user/account/nickname")
    @Transactional
    public ResponseEntity<NicknamePayload> rename(
            HttpServletRequest request,
            @RequestBody(required = false) NicknamePayload body
    ) {
        String nickname = normalizeNickname(body == null ? null : body.nickname());
        if (nickname != null && nickname.length() > NICKNAME_MAX) {
            return ResponseEntity.badRequest().build();
        }
        return currentSessionUser.id(request)
                .flatMap(userRepository::findById)
                .map(user -> {
                    user.rename(nickname);
                    userRepository.save(user);
                    return ResponseEntity.ok(NicknamePayload.of(nickname));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /** 앞뒤 공백을 빼고, 비면 null. */
    private static String normalizeNickname(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 계정을 탈퇴시키고 현재 세션 쿠키를 만료시킨다. */
    @PostMapping("/user/account/withdraw")
    public ResponseEntity<Void> withdraw(HttpServletRequest request, HttpServletResponse response) {
        return currentSessionUser.id(request)
                .flatMap(userRepository::findById)
                .map(user -> {
                    accountWithdrawalService.withdraw(user, sessionCookieFactory.read(request).orElse(null));
                    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookieFactory.clear().toString());
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
