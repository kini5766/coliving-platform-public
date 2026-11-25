package com.coliving.platform.security.dto;

/**
 * 재설정 확인 본문. token 은 메일 링크의 원문이다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
public record PasswordResetConfirmRequestDTO(String token, String password) {
}
