package com.coliving.platform.security.dao;

import com.coliving.platform.security.config.AuthProperties;
import com.coliving.platform.security.session.BrowserSession;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrowserSessionDAOTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> sets = mock(SetOperations.class);
    private final BrowserSessionDAO dao = new BrowserSessionDAO(
            new AuthProperties(null, null, null, null, Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(15)),
            redis
    );

    @Test
    void saveIndexesTheSessionByUser() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);

        dao.save("sid", new BrowserSession("9", "refresh", "access", 1L));

        verify(sets).add("auth:user-sessions:9", "sid");
        verify(redis).expire("auth:user-sessions:9", Duration.ofDays(7));
    }

    @Test
    void deleteRemovesTheSessionFromTheUserIndex() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        when(values.get("auth:session:sid")).thenReturn(
                "{\"sub\":\"9\",\"refreshToken\":\"refresh\",\"accessToken\":\"access\",\"accessExpiresAtEpochMilli\":1}"
        );

        dao.delete("sid");

        verify(sets).remove("auth:user-sessions:9", "sid");
        verify(redis).delete("auth:session:sid");
    }
}
