package com.coliving.platform.security.oauth;

import com.coliving.platform.security.dao.OAuthTransactionDAO;
import com.coliving.platform.security.http.OAuthTransactionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * 소셜 인가 요청을 세션 대신 Redis 와 oauth_tx 쿠키에 둔다.
 * 콜백은 state 가 같을 때만 요청을 복원한다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@Component
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final OAuthTransactionDAO transactionDAO;
    private final OAuthTransactionCookie transactionCookie;

    /** 쿠키와 state 가 맞는 인가 요청을 읽는다. 지우지는 않는다. */
    @Override
    public @Nullable OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Assert.notNull(request, "request cannot be null");
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        return transactionCookie.read(request)
                .flatMap(transactionDAO::find)
                .map(OAuthTransaction::toRequest)
                .filter(saved -> state.equals(saved.getState()))
                .orElse(null);
    }

    /** 새 트랜잭션을 저장하고 이전 쿠키 트랜잭션은 지운다. null 이면 비운다. */
    @Override
    public void saveAuthorizationRequest(
            @Nullable OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Assert.notNull(request, "request cannot be null");
        Assert.notNull(response, "response cannot be null");
        if (authorizationRequest == null) {
            clear(request, response);
            return;
        }
        Assert.hasText(authorizationRequest.getState(), "authorizationRequest.state cannot be empty");
        transactionCookie.read(request).ifPresent(transactionDAO::delete);
        String id = transactionDAO.newId();
        transactionDAO.save(id, OAuthTransaction.from(authorizationRequest));
        response.addHeader(HttpHeaders.SET_COOKIE, transactionCookie.write(id).toString());
    }

    /** 콜백에서 트랜잭션을 지우고 return_to 를 요청에 남긴다. */
    @Override
    public @Nullable OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Assert.notNull(request, "request cannot be null");
        Assert.notNull(response, "response cannot be null");
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        Optional<String> id = transactionCookie.read(request);
        if (state == null || state.isBlank() || id.isEmpty()) {
            return null;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, transactionCookie.clear().toString());
        return transactionDAO.findAndDelete(id.get())
                .map(OAuthTransaction::toRequest)
                .filter(saved -> state.equals(saved.getState()))
                .map(saved -> {
                    // The callback deletes this transaction before the success handler runs.
                    SocialReturnPath.remember(request, saved);
                    return saved;
                })
                .orElse(null);
    }

    /** 쿠키와 Redis 트랜잭션을 함께 지운다. */
    private void clear(HttpServletRequest request, HttpServletResponse response) {
        transactionCookie.read(request).ifPresent(transactionDAO::delete);
        response.addHeader(HttpHeaders.SET_COOKIE, transactionCookie.clear().toString());
    }
}
