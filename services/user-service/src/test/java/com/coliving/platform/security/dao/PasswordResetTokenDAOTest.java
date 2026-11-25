package com.coliving.platform.security.dao;

import com.coliving.platform.common.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetTokenDAOTest {

    @Test
    void issueStoresTheHashAndNotTheRawToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("auth:password-reset:user:7")).thenReturn(null);
        PasswordResetTokenDAO dao = dao(redis);

        String raw = dao.issue(7L);

        String hash = RefreshTokenDAO.hash(raw);
        verify(values).set(eq("auth:password-reset:" + hash), eq("7"), eq(Duration.ofMinutes(30)));
        verify(values).set(eq("auth:password-reset:user:7"), eq(hash), eq(Duration.ofMinutes(30)));
        assertThat(raw).doesNotContain(hash);
    }

    @Test
    void consumeReadsTheHashOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(any())).thenReturn("7");
        when(values.get(any())).thenReturn("stored-hash");
        PasswordResetTokenDAO dao = dao(redis);

        Optional<Long> userId = dao.consume("raw-token");

        assertThat(userId).contains(7L);
        verify(values).getAndDelete("auth:password-reset:" + RefreshTokenDAO.hash("raw-token"));
    }

    private static PasswordResetTokenDAO dao(StringRedisTemplate redis) {
        MailProperties mailProperties = new MailProperties(
                true, "roomlink@gmail.com", "Roomlink", "http://localhost:5173", Duration.ofMinutes(30));
        return new PasswordResetTokenDAO(redis, mailProperties);
    }
}
