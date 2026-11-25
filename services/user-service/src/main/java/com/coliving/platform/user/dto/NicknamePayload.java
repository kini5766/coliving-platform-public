package com.coliving.platform.user.dto;

/**
 * 닉네임 변경 요청·응답. 빈 닉네임은 빈 문자열로 나간다.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
public record NicknamePayload(String nickname) {
    /** null 닉네임을 빈 문자열로 맞춘다. */
    public static NicknamePayload of(String nickname) {
        return new NicknamePayload(nickname == null ? "" : nickname);
    }
}
