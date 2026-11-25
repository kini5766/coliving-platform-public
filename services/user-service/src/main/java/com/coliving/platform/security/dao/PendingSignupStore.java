package com.coliving.platform.security.dao;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 확인 전 가입. 회원 행을 만들지 않고 이메일과 비밀번호 해시만 7일 보관한다.
 * 다시 보내도 처음 시각부터 센 만료는 늘리지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Component
@RequiredArgsConstructor
public class PendingSignupStore {

    public static final Duration RETENTION = Duration.ofDays(7);
    private static final String PREFIX = "auth:signup-pending:";

    private final StringRedisTemplate redis;

    /** 이메일과 비밀번호 해시를 남은 시간만큼 저장한다. 시간이 없거나 해시가 비면 지운다. */
    public void save(String email, String passwordHash, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || passwordHash == null || passwordHash.isBlank()) {
            delete(email);
            return;
        }
        Duration kept = ttl.truncatedTo(ChronoUnit.SECONDS);
        if (kept.isZero()) {
            kept = Duration.ofSeconds(1);
        }
        redis.opsForValue().set(key(email), email + "\n" + passwordHash, kept);
    }

    /** 남은 보관 시간. 없거나 만료면 비운다. */
    public Optional<Duration> remaining(String email) {
        Long seconds = redis.getExpire(key(email), TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    /** 이메일로 대기 가입을 읽는다. 형식이 깨졌으면 비운다. */
    public Optional<PendingSignup> find(String email) {
        return decode(redis.opsForValue().get(key(email)));
    }

    /** 이메일 해시로 대기 가입을 읽는다. 해시가 비면 비운다. */
    public Optional<PendingSignup> findByEmailHash(String emailHash) {
        if (emailHash == null || emailHash.isBlank()) {
            return Optional.empty();
        }
        return decode(redis.opsForValue().get(PREFIX + emailHash));
    }

    /** 그 이메일의 대기 가입을 지운다. */
    public void delete(String email) {
        redis.delete(key(email));
    }

    /** 이메일을 해시해 Redis 키를 만든다. */
    private static String key(String email) {
        return PREFIX + RefreshTokenDAO.hash(email);
    }

    /** 저장 문자열을 이메일과 비밀번호 해시로 나눈다. 줄바꿈이 없으면 비운다. */
    private static Optional<PendingSignup> decode(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        int split = raw.indexOf('\n');
        if (split <= 0 || split >= raw.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new PendingSignup(raw.substring(0, split), raw.substring(split + 1)));
    }

    /**
     * 대기 중인 가입. passwordHash 는 인코딩된 값이다.
     *
     * @author 조휘재
     * 작성일: 2026-11-11
     */
    public record PendingSignup(String email, String passwordHash) {
    }
}
