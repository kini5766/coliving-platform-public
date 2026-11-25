package com.coliving.platform.security.service;

import com.coliving.platform.security.config.AuthProperties;
import com.coliving.platform.security.dao.BrowserSessionDAO;
import com.coliving.platform.security.dao.RefreshTokenDAO;
import com.coliving.platform.security.jwt.AccessTokenProvider;
import com.coliving.platform.security.session.BrowserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 브라우저 세션의 발급, 조회, 액세스 토큰 갱신, 로그아웃, 회원 단위 폐기.
 * 쿠키 값은 세션 번호이고, 액세스·리프레시 토큰은 Redis 에만 둔다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenDAO refreshTokenDAO;
    private final BrowserSessionDAO browserSessionDAO;
    private final CustomUserDetailsService userDetailsService;
    private final AuthProperties authProperties;

    private final UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();

    /** 새 세션을 만들고 세션 번호를 돌려준다. 로그인은 기존 세션을 돌리지 않는다. */
    public String openSession(String accessSub) {
        return browserSessionDAO.saveNew(issue(accessSub));
    }

    /** 세션의 회원 번호. 세션이 없으면 비운다. */
    public Optional<String> sessionSubject(String sessionId) {
        return browserSessionDAO.find(sessionId).map(BrowserSession::sub);
    }

    /**
     * 아직 갱신 여유 밖이면 저장된 액세스 토큰을 그대로 돌려준다.
     * 여유 안이면 리프레시 토큰을 소비하고 액세스 토큰만 다시 발급해 같은 세션에 덮어쓴다.
     */
    public Optional<String> currentAccessToken(String sessionId) {
        Optional<BrowserSession> stored = browserSessionDAO.find(sessionId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        BrowserSession session = stored.get();
        if (accessStillValid(session)) {
            return Optional.of(session.accessToken());
        }

        Optional<String> rotatedSub = refreshTokenDAO.findAndDelete(session.refreshToken());
        if (rotatedSub.isEmpty()) {
            return browserSessionDAO.find(sessionId)
                    .filter(latest -> latest.accessExpiresAtEpochMilli() > Instant.now().toEpochMilli())
                    .map(BrowserSession::accessToken);
        }
        if (!rotatedSub.get().equals(session.sub())) {
            browserSessionDAO.delete(sessionId);
            return Optional.empty();
        }

        BrowserSession renewed = issue(rotatedSub.get());
        browserSessionDAO.save(sessionId, renewed);
        return Optional.of(renewed.accessToken());
    }

    /** 이 세션과 그 리프레시 토큰만 지운다. */
    public void closeSession(String sessionId) {
        browserSessionDAO.find(sessionId)
                .ifPresent(session -> refreshTokenDAO.deleteTokenHash(session.refreshToken()));
        browserSessionDAO.delete(sessionId);
    }

    /** 인덱스에 있는 세션과 현재 쿠키 세션을 끊고 회원별 인덱스를 비운다. */
    public void revokeUser(String userId, String currentSessionId) {
        Set<String> sessionIds = new HashSet<>(browserSessionDAO.sessionIds(userId));
        if (currentSessionId != null && !currentSessionId.isBlank()) {
            sessionIds.add(currentSessionId);
        }
        sessionIds.forEach(this::closeSession);
        browserSessionDAO.clearSessionIndex(userId);
    }

    /** 계정을 확인한 뒤 액세스·리프레시 토큰을 발급해 세션 값을 만든다. */
    private BrowserSession issue(String accessSub) {
        UserDetails userDetails = findAndValidateUser(accessSub);
        Instant accessExpiresAt = Instant.now().plus(authProperties.accessTokenExpiration());
        String refreshToken = refreshTokenDAO.generateAndSave(accessSub);
        String accessToken = accessTokenProvider.issueAccessToken(accessSub, userDetails.getAuthorities());
        return new BrowserSession(accessSub, refreshToken, accessToken, accessExpiresAt.toEpochMilli());
    }

    /** 만료 시각에서 갱신 여유를 뺀 시각이 아직 미래면 true. */
    private boolean accessStillValid(BrowserSession session) {
        return session.accessExpiresAtEpochMilli() - authProperties.accessTokenRefreshSkew().toMillis() > Instant.now().toEpochMilli();
    }

    /** 회원 번호로 계정을 읽고 잠금·비활성 여부를 확인한다. */
    private UserDetails findAndValidateUser(String accessSub) {
        UserDetails userDetails = userDetailsService.loadUserById(accessSub);
        userDetailsChecker.check(userDetails);
        return userDetails;
    }
}
