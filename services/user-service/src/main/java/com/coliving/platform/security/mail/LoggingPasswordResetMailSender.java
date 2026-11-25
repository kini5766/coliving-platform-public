package com.coliving.platform.security.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 메일이 꺼져 있으면 재설정 메일을 보내지 않는다. 링크는 로그에 남기지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    /** 발송하지 않고 수신 주소만 로그에 남긴다. */
    @Override
    public void send(String to, String resetUrl) {
        log.info("[메일 미발송: app.mail.enabled=false] to={}", to);
    }
}
