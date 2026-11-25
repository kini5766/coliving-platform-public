package com.coliving.platform.security.dao;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 가입 확인 토큰은 해시만 저장한다. 원문은 확인 메일로만 나간다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Component
@RequiredArgsConstructor
public class SignupEmailVerificationTokenDAO {

    public static final Duration TTL = Duration.ofHours(24);
    private static final String TOKEN_PREFIX = "auth:signup-email:";
    private static final String USER_PREFIX = "auth:signup-email:user:";
    private static final String EMAIL_MARKER = "e:";

    private final StringRedisTemplate redis;

    /** 새 원문 토큰을 만들고 해시만 저장한다. 같은 이메일의 이전 토큰은 지운다. 24시간이다. */
    public String issue(String normalizedEmail) {
        String emailHash = RefreshTokenDAO.hash(normalizedEmail);
        String raw = RefreshTokenDAO.generateOpaqueRefreshToken();
        String tokenHash = RefreshTokenDAO.hash(raw);
        String indexKey = USER_PREFIX + emailHash;
        String previous = redis.opsForValue().get(indexKey);
        if (previous != null) {
            redis.delete(TOKEN_PREFIX + previous);
        }
        redis.opsForValue().set(TOKEN_PREFIX + tokenHash, EMAIL_MARKER + emailHash, TTL);
        redis.opsForValue().set(indexKey, tokenHash, TTL);
        return raw;
    }

    /** 원문 토큰을 지우지 않고 대상만 읽는다. 없거나 형식이 아니면 비운다. */
    public Optional<Subject> lookup(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String stored = redis.opsForValue().get(TOKEN_PREFIX + RefreshTokenDAO.hash(rawToken));
        if (stored == null) {
            return Optional.empty();
        }
        return parse(stored);
    }

    /** 원문 토큰을 한 번 쓰고 지운다. 없거나 이미 썼으면 그대로 끝낸다. */
    public void consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = RefreshTokenDAO.hash(rawToken);
        String stored = redis.opsForValue().getAndDelete(TOKEN_PREFIX + tokenHash);
        if (stored == null) {
            return;
        }
        String indexKey = indexKey(stored);
        String current = redis.opsForValue().get(indexKey);
        if (tokenHash.equals(current)) {
            redis.delete(indexKey);
        }
    }

    /** 그 이메일의 확인 토큰을 지운다. */
    public void revokeEmail(String normalizedEmail) {
        deleteIndex(USER_PREFIX + RefreshTokenDAO.hash(normalizedEmail));
    }

    /** 예전에 회원 번호로 발급한 링크. */
    public void revokeUser(long userId) {
        deleteIndex(USER_PREFIX + userId);
    }

    /** 색인의 토큰 해시와 그 토큰을 지운다. */
    private void deleteIndex(String indexKey) {
        String tokenHash = redis.opsForValue().getAndDelete(indexKey);
        if (tokenHash != null) {
            redis.delete(TOKEN_PREFIX + tokenHash);
        }
    }

    /** 저장된 값으로 이메일 또는 회원 번호 색인 키를 만든다. */
    private static String indexKey(String stored) {
        if (stored.startsWith(EMAIL_MARKER)) {
            return USER_PREFIX + stored.substring(EMAIL_MARKER.length());
        }
        return USER_PREFIX + stored;
    }

    /** e: 로 시작하면 이메일 해시, 숫자면 예전 회원 번호. 둘 다 아니면 비운다. */
    static Optional<Subject> parse(String stored) {
        if (stored.startsWith(EMAIL_MARKER)) {
            String emailHash = stored.substring(EMAIL_MARKER.length());
            if (!isSha256Hex(emailHash)) {
                return Optional.empty();
            }
            return Optional.of(new PendingEmail(emailHash));
        }
        try {
            if (stored.isEmpty() || stored.length() > 19) {
                return Optional.empty();
            }
            for (int i = 0; i < stored.length(); i++) {
                if (!Character.isDigit(stored.charAt(i))) {
                    return Optional.empty();
                }
            }
            return Optional.of(new LegacyUser(Long.parseLong(stored)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /** 소문자 SHA-256 64자리인지 본다. */
    private static boolean isSha256Hex(String value) {
        if (value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /**
     * 확인 링크의 대상. 새 가입은 이메일 해시, 예전 링크는 회원 번호다.
     *
     * @author 조휘재
     * 작성일: 2026-11-11
     */
    public sealed interface Subject permits PendingEmail, LegacyUser {
    }

    /**
     * 확인 때 회원을 만든다. 값은 이메일 해시라 주소 원문은 토큰 키에 없다.
     *
     * @author 조휘재
     * 작성일: 2026-11-11
     */
    public record PendingEmail(String emailHash) implements Subject {
    }

    /**
     * 이 변경 전에 만들어진 링크. 값은 회원 번호다.
     *
     * @author 조휘재
     * 작성일: 2026-11-11
     */
    public record LegacyUser(long userId) implements Subject {
    }
}
