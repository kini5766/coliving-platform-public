package com.coliving.platform.security.entity;

import com.coliving.platform.user.entity.Role;
import com.coliving.platform.user.entity.UserEntity;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 이메일 로그인 주체.
 * getUsername 은 회원 번호 문자열이고, 권한 이름은 ROLE_ 로 시작한다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
public class CustomUserDetails implements UserDetails {

    private final String sub;
    private final List<GrantedAuthority> authorities;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;

    /** 회원과 비밀번호 해시로 만든다. 소셜 전용 계정이면 비밀번호는 null. */
    public CustomUserDetails(UserEntity entity, String password) {
        this.sub = String.valueOf(entity.getId());
        this.password = password;
        this.enabled = entity.isEnabled();
        this.accountNonLocked = entity.isAccountNonLocked();
        this.authorities = toAuthorities(entity.getRoles());
    }

    public static final String ROLE = "ROLE_";

    /** ROLE_ 접두사를 붙인 권한 이름. */
    public static String toAuthorityName(Role role) {
        return ROLE + role.name();
    }

    /** 역할 집합을 GrantedAuthority 로 바꾼다. */
    public static List<GrantedAuthority> toAuthorities(Collection<Role> roles) {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(toAuthorityName(role)))
                .toList();
    }

    /** 회원 번호 문자열. 이메일이 아니다. */
    @Override
    public @NonNull String getUsername() {
        return sub;
    }

    /** ROLE_ 이 붙은 권한. */
    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** 이메일 로그인 계정이 없으면 null. */
    @Override
    public String getPassword() {
        return password;
    }

    /** 잠금이 아니면 true. */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /** 탈퇴·비활성이 아니면 true. */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}