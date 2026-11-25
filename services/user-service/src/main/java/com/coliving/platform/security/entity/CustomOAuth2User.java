package com.coliving.platform.security.entity;

import com.coliving.platform.user.entity.UserEntity;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 소셜 로그인 주체.
 * getName 은 로컬 회원 번호이고, 제공자 속성은 권한 판단에 쓰지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@NullMarked
public class CustomOAuth2User implements OAuth2User {

    private final String sub;
    private final List<GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    /** 제공자 속성과 로컬 회원을 묶는다. */
    public CustomOAuth2User(Map<String, Object> attributes, UserEntity user) {
        this.sub = String.valueOf(user.getId());
        this.attributes = Map.copyOf(attributes);
        this.authorities = CustomUserDetails.toAuthorities(user.getRoles());
    }

    /** 로컬 회원 번호. */
    @Override
    public String getName() {
        return sub;
    }

    /** 제공자가 준 프로필 속성. */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /** 로컬 회원의 ROLE_ 권한. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
