package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.RefreshTokenDAO;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 가입 확인 메일 횟수. 첫 발송과 다시 보내기가 같은 한도를 쓴다.
 * 이메일 30분에 3회, IP 한 시간에 10회.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Component
@RequiredArgsConstructor
public class SignupMailRateLimiter {

    static final int EMAIL_LIMIT = 3;
    static final Duration EMAIL_WINDOW = Duration.ofMinutes(30);
    static final int IP_LIMIT = 10;
    static final Duration IP_WINDOW = Duration.ofHours(1);

    private static final String EMAIL_PREFIX = "auth:signup-email:rate:email:";
    private static final String IP_PREFIX = "auth:signup-email:rate:ip:";

    private final StringRedisTemplate redis;

    /** 이메일과 IP 한도를 모두 넘지 않았을 때만 true. 키는 해시로 저장한다. */
    public boolean allow(String normalizedEmail, String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        boolean emailAllowed = count(EMAIL_PREFIX + RefreshTokenDAO.hash(normalizedEmail), EMAIL_LIMIT, EMAIL_WINDOW);
        boolean ipAllowed = count(IP_PREFIX + RefreshTokenDAO.hash(ip), IP_LIMIT, IP_WINDOW);
        return emailAllowed && ipAllowed;
    }

    /** 창 안에서 횟수를 올리고 한도 이하면 true. 첫 증가 때 TTL 을 건다. */
    private boolean count(String key, int limit, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return false;
        }
        if (count == 1L || missingTtl(key)) {
            redis.expire(key, window);
        }
        return count <= limit;
    }

    /** TTL 이 없으면 true. increment 와 expire 가 어긋난 경우를 맞춘다. */
    private boolean missingTtl(String key) {
        Long ttl = redis.getExpire(key);
        return ttl == null || ttl < 0;
    }
}
