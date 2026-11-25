package com.coliving.platform.security.controller;

import com.coliving.platform.security.dto.PasswordResetConfirmRequestDTO;
import com.coliving.platform.security.dto.PasswordResetRequestDTO;
import com.coliving.platform.security.service.PasswordResetService;
import com.coliving.platform.security.service.PasswordResetService.InvalidPasswordResetException;
import com.coliving.platform.security.service.PasswordResetService.InvalidPasswordResetTokenException;
import com.coliving.platform.security.service.PasswordResetService.MailDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 재설정 요청과 확인.
 * 계정 유무는 응답으로 구분하지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@RestController
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /** 재설정 메일을 예약한다. 메일이 꺼져 있으면 503, 이메일 형식이 아니면 400. */
    @PostMapping("/user/password-reset")
    public ResponseEntity<Map<String, String>> requestReset(
            @RequestBody PasswordResetRequestDTO request, HttpServletRequest httpRequest) {
        try {
            passwordResetService.requestReset(request.email(), clientIp(httpRequest));
            return ResponseEntity.noContent().build();
        } catch (InvalidPasswordResetException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
        } catch (MailDisabledException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "MAIL_DISABLED"));
        }
    }

    /** 메일 링크의 토큰으로 비밀번호를 바꾼다. 토큰이 없거나 이미 썼으면 400. */
    @PostMapping("/user/password-reset/confirm")
    public ResponseEntity<Map<String, String>> confirm(@RequestBody PasswordResetConfirmRequestDTO request) {
        try {
            passwordResetService.confirm(request.token(), request.password());
            return ResponseEntity.noContent().build();
        } catch (InvalidPasswordResetException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
        } catch (InvalidPasswordResetTokenException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_TOKEN"));
        }
    }

    /** 횟수 제한용 IP. 게이트웨이를 거치면 X-Forwarded-For 첫 값이다. */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
