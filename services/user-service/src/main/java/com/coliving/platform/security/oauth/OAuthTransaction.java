package com.coliving.platform.security.oauth;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis 에 넣을 소셜 인가 요청. 문자열만 담아 JSON 으로 직렬화한다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
public record OAuthTransaction(
        String authorizationUri,
        String clientId,
        String redirectUri,
        Set<String> scopes,
        String state,
        String authorizationRequestUri,
        Map<String, String> additionalParameters,
        Map<String, String> attributes
) {
    /** null 컬렉션은 빈 불변 컬렉션으로 맞춘다. */
    public OAuthTransaction {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        additionalParameters = additionalParameters == null ? Map.of() : Map.copyOf(additionalParameters);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Spring 인가 요청에서 만든다. 문자열이 아닌 파라미터는 거부한다. */
    public static OAuthTransaction from(OAuth2AuthorizationRequest request) {
        return new OAuthTransaction(
                request.getAuthorizationUri(),
                request.getClientId(),
                request.getRedirectUri(),
                request.getScopes(),
                request.getState(),
                request.getAuthorizationRequestUri(),
                textValues(request.getAdditionalParameters()),
                textValues(request.getAttributes())
        );
    }

    /** 저장본을 Spring 인가 요청으로 되돌린다. authorizationRequestUri 도 유지한다. */
    public OAuth2AuthorizationRequest toRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationUri)
                .clientId(clientId)
                .redirectUri(redirectUri)
                .scopes(scopes)
                .state(state)
                .additionalParameters(new LinkedHashMap<>(additionalParameters))
                .attributes(new LinkedHashMap<>(attributes))
                .authorizationRequestUri(authorizationRequestUri)
                .build();
    }

    /** 맵 값이 모두 문자열인지 확인하고 복사한다. */
    private static Map<String, String> textValues(Map<String, Object> source) {
        Map<String, String> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(value instanceof String text)) {
                throw new IllegalStateException("oauth parameter must be text: " + key);
            }
            values.put(key, text);
        });
        return values;
    }
}
