package com.coliving.platform.security.dto;

/**
 * 비밀번호 재설정 요청 본문.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
public record PasswordResetRequestDTO(String email) {
}
