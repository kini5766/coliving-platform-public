package com.coliving.platform.security.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 화면에 주는 회원 한 줄. 탈퇴하면 email 은 null 이다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
public record AdminMemberResponse(
        long id,
        String nickname,
        String email,
        List<String> roles,
        List<String> providers,
        boolean enabled,
        LocalDateTime createdAt
) {
}
