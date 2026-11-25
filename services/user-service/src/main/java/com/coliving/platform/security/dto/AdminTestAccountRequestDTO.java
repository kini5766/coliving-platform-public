package com.coliving.platform.security.dto;

/**
 * 관리자 테스트 계정 본문. 비밀번호는 평문이다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
public record AdminTestAccountRequestDTO(String email, String password) {
}
