package com.coliving.platform.security.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 세션 응답. 토큰은 없고, null 필드는 JSON 에서 빠진다.
 *
 * @author 조휘재
 * 작성일: 2026-10-27
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponseDTO(
        boolean authenticated,
        String userId,
        String nickname
) {
}
