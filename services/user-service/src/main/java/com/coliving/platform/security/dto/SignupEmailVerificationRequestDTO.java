package com.coliving.platform.security.dto;

/**
 * 가입 확인 본문. token 은 메일에 실은 원문이다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
public record SignupEmailVerificationRequestDTO(String token) {
}
