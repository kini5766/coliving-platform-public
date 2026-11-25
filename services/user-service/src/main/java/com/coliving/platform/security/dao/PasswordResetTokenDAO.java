package com.coliving.platform.security.dao;

import com.coliving.platform.common.config.MailProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 재설정 토큰은 해시만 저장한다. 원문은 메일로만 나간다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Component
@RequiredArgsConstructor
public class PasswordResetTokenDAO {

    private static final String TOKEN_PREFIX = "auth:password-reset:";
    private static final String USER_PREFIX = "auth:password-reset:user:";

    private final StringRedisTemplate redis;
    private final MailProperties mailProperties;

    /** 새 원문 토큰을 만들고 해시만 저장한다. 같은 회원의 이전 토큰은 지운다. */
    public String issue(long userId) {
        String raw = RefreshTokenDAO.generateOpaqueRefreshToken();
        String hash = RefreshTokenDAO.hash(raw);
        String userKey = USER_PREFIX + userId;
        String previous = redis.opsForValue().get(userKey);
        if (previous != null) {
            redis.delete(TOKEN_PREFIX + previous);
        }
        redis.opsForValue().set(TOKEN_PREFIX + hash, Long.toString(userId), mailProperties.resetTokenTtl());
        redis.opsForValue().set(userKey, hash, mailProperties.resetTokenTtl());
        return raw;
    }

    /** 원문 토큰을 한 번 쓰고 지운다. 없거나 이미 썼으면 비운다. */
    public Optional<Long> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = RefreshTokenDAO.hash(rawToken);
        String userId = redis.opsForValue().getAndDelete(TOKEN_PREFIX + hash);
        if (userId == null) {
            return Optional.empty();
        }
        String userKey = USER_PREFIX + userId;
        String current = redis.opsForValue().get(userKey);
        if (hash.equals(current)) {
            redis.delete(userKey);
        }
        try {
            return Optional.of(Long.valueOf(userId));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** 그 회원의 재설정 토큰을 지운다. */
    public void revoke(long userId) {
        String hash = redis.opsForValue().getAndDelete(USER_PREFIX + userId);
        if (hash != null) {
            redis.delete(TOKEN_PREFIX + hash);
        }
    }
}
