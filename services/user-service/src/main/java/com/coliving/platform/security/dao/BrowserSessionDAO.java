package com.coliving.platform.security.dao;

import com.coliving.platform.security.config.AuthProperties;
import com.coliving.platform.security.session.BrowserSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * 브라우저 세션을 Redis 에 둔다.
 * 본문은 auth:session:{sessionId}, 회원별 세션 번호 집합은 auth:user-sessions:{userId} 이다.
 * TTL 은 리프레시 토큰 만료와 같다.
 *
 * @author 조휘재
 * 작성일: 2026-10-27
 */
@Component
@RequiredArgsConstructor
public class BrowserSessionDAO {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String KEY_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";

    private final AuthProperties authProperties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 새 세션 번호를 만들어 저장하고 그 번호를 돌려준다. */
    public String saveNew(BrowserSession session) {
        String sessionId = newSessionId();
        save(sessionId, session);
        return sessionId;
    }

    /** 세션 JSON 을 저장하고 회원별 인덱스에 번호를 넣는다. TTL 을 다시 맞춘다. */
    public void save(String sessionId, BrowserSession session) {
        redis.opsForValue().set(
                KEY_PREFIX + sessionId,
                write(session),
                authProperties.refreshTokenExpiration()
        );
        if (session.sub() != null && !session.sub().isBlank()) {
            String indexKey = USER_SESSIONS_PREFIX + session.sub();
            redis.opsForSet().add(indexKey, sessionId);
            redis.expire(indexKey, authProperties.refreshTokenExpiration());
        }
    }

    /** 세션을 읽는다. JSON 이 깨지면 그 키를 지우고 비운다. */
    public Optional<BrowserSession> find(String sessionId) {
        String json = redis.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, BrowserSession.class));
        } catch (JsonProcessingException ex) {
            redis.delete(KEY_PREFIX + sessionId);
            return Optional.empty();
        }
    }

    /** 회원에게 묶인 세션 번호. 인덱스가 없으면 빈 집합이다. */
    public Set<String> sessionIds(String userId) {
        Set<String> sessionIds = redis.opsForSet().members(USER_SESSIONS_PREFIX + userId);
        return sessionIds == null ? Set.of() : Set.copyOf(sessionIds);
    }

    /** 회원별 세션 번호 집합을 지운다. 세션 본문은 따로 지워야 한다. */
    public void clearSessionIndex(String userId) {
        redis.delete(USER_SESSIONS_PREFIX + userId);
    }

    /** 세션 본문과 인덱스에서 그 번호를 뺀다. */
    public void delete(String sessionId) {
        find(sessionId).ifPresent(session -> {
            if (session.sub() != null) {
                redis.opsForSet().remove(USER_SESSIONS_PREFIX + session.sub(), sessionId);
            }
        });
        redis.delete(KEY_PREFIX + sessionId);
    }

    /** 세션을 JSON 으로 바꾼다. */
    private String write(BrowserSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("session serialize failed", ex);
        }
    }

    /** URL-safe 세션 번호. 32바이트 난수다. */
    private static String newSessionId() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
