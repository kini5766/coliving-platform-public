package com.coliving.platform.security.dto;

/**
 * 이메일 회원가입 본문.
 *
 * @author 조휘재
 * 작성일: 2026-10-30
 */
public record SignupRequestDTO(String email, String password) {
}
