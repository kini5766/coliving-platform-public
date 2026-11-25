package com.coliving.platform.security.mail;

import com.coliving.platform.security.dao.PasswordResetTokenDAO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 재설정 메일은 HTTP 응답을 돌려준 뒤에 보낸다.
 * 발송 실패는 토큰을 지우고 로그만 남긴다. 응답 코드로 계정 유무가 드러나지 않게 한다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailDispatcher {

    static final String EXECUTOR = "passwordResetMailExecutor";

    private final PasswordResetMailSender passwordResetMailSender;
    private final PasswordResetTokenDAO passwordResetTokenDAO;

    /** 응답 이후에 메일을 보낸다. 실패하면 토큰을 지운다. */
    @Async(EXECUTOR)
    public void send(long userId, String email, String resetUrl) {
        try {
            passwordResetMailSender.send(email, resetUrl);
        } catch (RuntimeException ex) {
            passwordResetTokenDAO.revoke(userId);
            log.warn("비밀번호 재설정 메일 발송 실패. to={}", email, ex);
        }
    }
}
