package com.coliving.platform.security.service;

import com.coliving.platform.security.entity.CustomOidcUser;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 구글처럼 openid 스코프인 로그인.
 * OidcUserService 로 클레임을 받은 뒤 로컬 회원과 연결한다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final CustomOAuth2UserService customOAuth2UserService;

    /** OIDC 사용자 서비스와 로컬 연결 서비스를 묶는다. */
    public CustomOidcUserService(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    /** ID 토큰과 userinfo 클레임을 합쳐 로컬 회원에 연결한다. */
    @Override
    public OidcUser loadUser(@NonNull OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        Map<String, Object> attributes = new HashMap<>(oidcUser.getClaims());
        OidcUserInfo userInfo = oidcUser.getUserInfo();
        if (userInfo != null) {
            attributes.putAll(userInfo.getClaims());
        }
        return new CustomOidcUser(
                customOAuth2UserService.loadLinkedUser(
                        userRequest.getClientRegistration().getRegistrationId(),
                        attributes),
                oidcUser);
    }
}
