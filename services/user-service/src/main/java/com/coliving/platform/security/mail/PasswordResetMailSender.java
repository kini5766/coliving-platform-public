package com.coliving.platform.security.mail;

/**
 * 비밀번호 재설정 메일. 링크는 이 메일을 받는 주소로만 간다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
public interface PasswordResetMailSender {
    /** to 로만 재설정 링크를 보낸다. */
    void send(String to, String resetUrl);
}
