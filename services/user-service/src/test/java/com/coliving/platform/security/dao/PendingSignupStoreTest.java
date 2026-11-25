package com.coliving.platform.security.dao;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingSignupStoreTest {

    @Test
    void saveKeepsTheEmailOutOfTheKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        PendingSignupStore store = new PendingSignupStore(redis);
        String key = "auth:signup-pending:" + RefreshTokenDAO.hash("person@gmail.com");

        store.save("person@gmail.com", "{noop}secret", PendingSignupStore.RETENTION);

        verify(values).set(eq(key), eq("person@gmail.com\n{noop}secret"), eq(Duration.ofDays(7)));
        assertThat(key).doesNotContain("person@gmail.com");
    }

    @Test
    void findReadsTheStoredSignup() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String key = "auth:signup-pending:" + RefreshTokenDAO.hash("person@gmail.com");
        when(values.get(key)).thenReturn("person@gmail.com\n{noop}secret");
        when(redis.getExpire(key, TimeUnit.SECONDS)).thenReturn(3600L);
        PendingSignupStore store = new PendingSignupStore(redis);

        assertThat(store.find("person@gmail.com"))
                .contains(new PendingSignupStore.PendingSignup("person@gmail.com", "{noop}secret"));
        assertThat(store.remaining("person@gmail.com")).contains(Duration.ofHours(1));
    }
}
