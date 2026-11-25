package com.coliving.platform.user.dto;

import com.coliving.platform.user.mock.ResidenceMock;

/**
 * 프로필 응답. residence 는 계약 서비스 연동 전 자리표시자다.
 *
 * @author 조휘재
 * 작성일: 2026-11-02
 */
public record ProfileResponse(String nickname, ResidenceMock residence) {
}
