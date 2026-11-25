package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.RefreshTokenDAO;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetRateLimiterTest {

    private static final String EMAIL = "resident-a@test.com";
    private static final String IP = "203.0.113.5";

    @Test
    void thirdEmailAndTenthIpAreAllowed() {
        StringRedisTemplate redis = redis();
        ValueOperations<String, String> values = values(redis);
        String emailKey = emailKey();
        String ipKey = ipKey();
        when(values.increment(emailKey)).thenReturn(3L);
        when(redis.getExpire(emailKey)).thenReturn(1000L);
        when(values.increment(ipKey)).thenReturn(10L);
        when(redis.getExpire(ipKey)).thenReturn(1000L);

        assertThat(new PasswordResetRateLimiter(redis).allow(EMAIL, IP)).isTrue();
        verify(redis, never()).expire(emailKey, PasswordResetRateLimiter.EMAIL_WINDOW);
    }

    @Test
    void fourthEmailIsDeniedAndTheIpIsStillCounted() {
        StringRedisTemplate redis = redis();
        ValueOperations<String, String> values = values(redis);
        String emailKey = emailKey();
        String ipKey = ipKey();
        when(values.increment(emailKey)).thenReturn(4L);
        when(redis.getExpire(emailKey)).thenReturn(1000L);
        when(values.increment(ipKey)).thenReturn(1L);

        assertThat(new PasswordResetRateLimiter(redis).allow(EMAIL, IP)).isFalse();
        verify(redis).expire(ipKey, Duration.ofHours(1));
    }

    @Test
    void eleventhIpIsDenied() {
        StringRedisTemplate redis = redis();
        ValueOperations<String, String> values = values(redis);
        String emailKey = emailKey();
        String ipKey = ipKey();
        when(values.increment(emailKey)).thenReturn(1L);
        when(values.increment(ipKey)).thenReturn(11L);
        when(redis.getExpire(ipKey)).thenReturn(1000L);

        assertThat(new PasswordResetRateLimiter(redis).allow(EMAIL, IP)).isFalse();
        verify(redis).expire(emailKey, Duration.ofMinutes(30));
    }

    @Test
    void keysStoreHashesNotTheAddress() {
        String emailKey = emailKey();
        String ipKey = ipKey();

        assertThat(emailKey).doesNotContain(EMAIL);
        assertThat(ipKey).doesNotContain(IP);
    }

    private static String emailKey() {
        return "auth:password-reset:rate:email:" + RefreshTokenDAO.hash(EMAIL);
    }

    private static String ipKey() {
        return "auth:password-reset:rate:ip:" + RefreshTokenDAO.hash(IP);
    }

    private static StringRedisTemplate redis() {
        return mock(StringRedisTemplate.class);
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> values(StringRedisTemplate redis) {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        return values;
    }
}
