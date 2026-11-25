package com.coliving.platform.security.dao;

import com.coliving.platform.common.config.AppProperties;
import com.coliving.platform.security.oauth.OAuthTransaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 소셜 로그인 진행 중 상태와 PKCE 를 Redis auth:oauth-tx:{id} 에 둔다.
 * TTL 은 쿠키 oauthTransactionMaxAge 와 같다.
 *
 * @author 조휘재
 * 작성일: 2026-10-28
 */
@Component
@RequiredArgsConstructor
public class OAuthTransactionDAO {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String KEY_PREFIX = "auth:oauth-tx:";

    private final AppProperties appProperties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 쿠키에 실을 트랜잭션 번호. 32바이트 난수다. */
    public String newId() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 인가 요청을 저장한다. */
    public void save(String id, OAuthTransaction transaction) {
        redis.opsForValue().set(
                KEY_PREFIX + id,
                write(transaction),
                appProperties.cookie().oauthTransactionMaxAge()
        );
    }

    /** 지우지 않고 읽는다. JSON 이 깨지면 키를 지운다. */
    public Optional<OAuthTransaction> find(String id) {
        return read(id, redis.opsForValue().get(KEY_PREFIX + id));
    }

    /** 읽고 바로 지운다. 콜백은 한 번만 쓴다. */
    public Optional<OAuthTransaction> findAndDelete(String id) {
        return read(id, redis.opsForValue().getAndDelete(KEY_PREFIX + id));
    }

    /** 트랜잭션을 지운다. */
    public void delete(String id) {
        redis.delete(KEY_PREFIX + id);
    }

    /** JSON 을 트랜잭션으로 읽는다. 깨지면 그 키를 지운다. */
    private Optional<OAuthTransaction> read(String id, String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OAuthTransaction.class));
        } catch (JsonProcessingException ex) {
            redis.delete(KEY_PREFIX + id);
            return Optional.empty();
        }
    }

    /** 트랜잭션을 JSON 으로 바꾼다. */
    private String write(OAuthTransaction transaction) {
        try {
            return objectMapper.writeValueAsString(transaction);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("oauth transaction serialize failed", ex);
        }
    }
}
