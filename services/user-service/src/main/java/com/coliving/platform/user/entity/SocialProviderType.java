package com.coliving.platform.user.entity;

import lombok.Getter;

/**
 * 지원하는 소셜 로그인. 네이버와 구글만 받는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@Getter
public enum SocialProviderType {
    NAVER("네이버"),
    GOOGLE("구글");

    private final String description;

    /** 화면에 보여줄 제공자 이름. */
    SocialProviderType(String description) {
        this.description = description;
    }
}
