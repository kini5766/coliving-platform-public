package com.coliving.platform.security.dao;

import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO.LegacyUser;
import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO.PendingEmail;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignupEmailVerificationTokenDAOTest {

    @Test
    void issueStoresTheEmailHashAndNotTheAddress() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String emailHash = RefreshTokenDAO.hash("person@gmail.com");
        when(values.get("auth:signup-email:user:" + emailHash)).thenReturn(null);
        SignupEmailVerificationTokenDAO dao = new SignupEmailVerificationTokenDAO(redis);

        String raw = dao.issue("person@gmail.com");

        String tokenHash = RefreshTokenDAO.hash(raw);
        verify(values).set(
                eq("auth:signup-email:" + tokenHash), eq("e:" + emailHash), eq(SignupEmailVerificationTokenDAO.TTL));
        verify(values).set(eq("auth:signup-email:user:" + emailHash), eq(tokenHash), eq(Duration.ofHours(24)));
        assertThat(raw).doesNotContain("person@gmail.com");
    }

    @Test
    void parseKeepsLegacyUserIdsAndNewEmailHashesApart() {
        String emailHash = RefreshTokenDAO.hash("person@gmail.com");

        assertThat(SignupEmailVerificationTokenDAO.parse("7")).contains(new LegacyUser(7L));
        assertThat(SignupEmailVerificationTokenDAO.parse("e:" + emailHash)).contains(new PendingEmail(emailHash));
        assertThat(SignupEmailVerificationTokenDAO.parse("not-a-token")).isEqualTo(Optional.empty());
    }
}
