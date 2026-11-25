package com.coliving.platform.security.controller;

import com.coliving.platform.security.dto.SignupEmailVerificationRequestDTO;
import com.coliving.platform.security.dto.SignupRequestDTO;
import com.coliving.platform.security.dto.SignupResendRequestDTO;
import com.coliving.platform.security.service.SignupService;
import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.security.service.SignupService.InvalidSignupException;
import com.coliving.platform.security.service.SignupService.InvalidSignupTokenException;
import com.coliving.platform.security.service.SignupService.MailDisabledException;
import com.coliving.platform.security.service.SignupService.MailSendException;
import com.coliving.platform.security.service.SignupService.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 회원가입. 성공은 201, 형식 오류는 400, 이미 쓴 이메일은 409.
 *
 * @author 조휘재
 * 작성일: 2026-10-30
 */
@RestController
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    /** 가입을 접수하고 확인 메일을 보낸다. 로그인 토큰은 주지 않는다. */
    @PostMapping("/user/signup")
    public ResponseEntity<Map<String, String>> signup(
            @RequestBody SignupRequestDTO request, HttpServletRequest httpRequest) {
        try {
            signupService.signup(request.email(), request.password(), clientIp(httpRequest));
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (InvalidSignupException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
        } catch (EmailAlreadyUsedException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "EMAIL_TAKEN"));
        } catch (MailDisabledException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "MAIL_DISABLED"));
        } catch (MailSendException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "MAIL_SEND_FAILED"));
        } catch (TooManyRequestsException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "TOO_MANY_REQUESTS"));
        }
    }

    /** 확인 메일을 다시 보낸다. 보낼 대상이 없어도 204. 형식 오류는 400, 메일 실패는 503, 한도 초과는 429. */
    @PostMapping("/user/signup/email-verification/resend")
    public ResponseEntity<Map<String, String>> resend(
            @RequestBody SignupResendRequestDTO request, HttpServletRequest httpRequest) {
        try {
            signupService.resend(request.email(), clientIp(httpRequest));
            return ResponseEntity.noContent().build();
        } catch (InvalidSignupException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
        } catch (MailDisabledException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "MAIL_DISABLED"));
        } catch (MailSendException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("code", "MAIL_SEND_FAILED"));
        } catch (TooManyRequestsException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", "TOO_MANY_REQUESTS"));
        }
    }

    /** 확인 링크로 가입을 마친다. 성공은 204, 없거나 만료면 400. */
    @PostMapping("/user/signup/email-verification")
    public ResponseEntity<Map<String, String>> confirmEmail(@RequestBody SignupEmailVerificationRequestDTO request) {
        try {
            signupService.confirmEmail(request.token());
            return ResponseEntity.noContent().build();
        } catch (InvalidSignupTokenException ex) {
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
