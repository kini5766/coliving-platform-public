package com.coliving.platform.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 인가 요청에 안전한 return_to 를 붙인다. 없거나 거절되면 원 요청을 그대로 둔다.
 *
 * @author 조휘재
 * 작성일: 2026-10-30
 */
public final class ReturnToAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    /** PKCE 가 이미 붙은 해석기에 return_to 보관을 얹는다. */
    public ReturnToAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    /** 등록 id 없이 해석한 요청에 return_to 를 붙인다. */
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return attach(delegate.resolve(request), request);
    }

    /** 등록 id 로 해석한 요청에 return_to 를 붙인다. */
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return attach(delegate.resolve(request, clientRegistrationId), request);
    }

    /** 안전한 return_to 만 속성으로 넣는다. authorizationRequestUri 는 유지한다. */
    private static OAuth2AuthorizationRequest attach(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request
    ) {
        if (authorizationRequest == null) {
            return null;
        }
        String returnTo = SocialReturnPath.sanitize(request.getParameter(SocialReturnPath.ATTRIBUTE));
        if (returnTo == null) {
            return authorizationRequest;
        }
        // from() does not copy authorizationRequestUri, and build() would generate a new one.
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .authorizationRequestUri(authorizationRequest.getAuthorizationRequestUri())
                .attributes(attributes -> attributes.put(SocialReturnPath.ATTRIBUTE, returnTo))
                .build();
    }
}
