package com.coliving.platform.security.mail;

/**
 * 회원가입 확인 메일. 링크는 가입한 주소로만 간다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
public interface SignupVerificationMailSender {
    /** to 로만 가입 확인 링크를 보낸다. */
    void send(String to, String confirmUrl);
}
