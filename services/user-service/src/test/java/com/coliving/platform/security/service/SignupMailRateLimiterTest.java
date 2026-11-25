package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.RefreshTokenDAO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignupMailRateLimiterTest {

    private static final String EMAIL = "person@gmail.com";
    private static final String IP = "203.0.113.5";

    @Test
    void fourthEmailIsDenied() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String emailKey = "auth:signup-email:rate:email:" + RefreshTokenDAO.hash(EMAIL);
        String ipKey = "auth:signup-email:rate:ip:" + RefreshTokenDAO.hash(IP);
        when(values.increment(emailKey)).thenReturn(4L);
        when(redis.getExpire(emailKey)).thenReturn(1000L);
        when(values.increment(ipKey)).thenReturn(1L);
        when(redis.getExpire(ipKey)).thenReturn(1000L);

        assertThat(new SignupMailRateLimiter(redis).allow(EMAIL, IP)).isFalse();
        assertThat(emailKey).doesNotContain(EMAIL);
    }

    @Test
    void thirdEmailIsAllowed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String emailKey = "auth:signup-email:rate:email:" + RefreshTokenDAO.hash(EMAIL);
        String ipKey = "auth:signup-email:rate:ip:" + RefreshTokenDAO.hash(IP);
        when(values.increment(emailKey)).thenReturn(3L);
        when(redis.getExpire(emailKey)).thenReturn(1000L);
        when(values.increment(ipKey)).thenReturn(10L);
        when(redis.getExpire(ipKey)).thenReturn(1000L);

        assertThat(new SignupMailRateLimiter(redis).allow(EMAIL, IP)).isTrue();
    }
}
