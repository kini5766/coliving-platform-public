package com.coliving.platform.security.entity;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

/**
 * 구글 OIDC 로그인 주체.
 * 이름과 권한은 로컬 회원이고, ID 토큰과 userinfo 는 제공자 값 그대로다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@NullMarked
public class CustomOidcUser implements OidcUser {

    private final CustomOAuth2User user;
    private final OidcUser delegate;

    /** 로컬 회원 주체와 제공자 OIDC 사용자를 묶는다. */
    public CustomOidcUser(CustomOAuth2User user, OidcUser delegate) {
        this.user = user;
        this.delegate = delegate;
    }

    /** 로컬 회원 번호. 제공자 sub 가 아니다. */
    @Override
    public String getName() {
        return user.getName();
    }

    /** 로컬 연결에 쓴 프로필 속성. */
    @Override
    public Map<String, Object> getAttributes() {
        return user.getAttributes();
    }

    /** 로컬 회원의 ROLE_ 권한. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getAuthorities();
    }

    /** ID 토큰 클레임. */
    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    /** 제공자 userinfo. 없으면 null. */
    @Override
    public @Nullable OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    /** 제공자 ID 토큰. */
    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }
}
