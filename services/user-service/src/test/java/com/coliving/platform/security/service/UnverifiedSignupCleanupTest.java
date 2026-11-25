package com.coliving.platform.security.service;

import com.coliving.platform.security.service.SignupService.InvalidSignupTokenException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnverifiedSignupCleanupTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T18:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UnverifiedUserDeleter deleter = mock(UnverifiedUserDeleter.class);
    private final UnverifiedSignupCleanup cleanup = new UnverifiedSignupCleanup(accounts, users, deleter, CLOCK);

    @Test
    void remainingIsEmptyAtSevenDays() {
        UserEntity user = user(LocalDateTime.of(2026, 11, 4, 3, 0));

        assertThat(cleanup.remaining(user)).isEmpty();
    }

    @Test
    void remainingKeepsTheRestOfTheSevenDays() {
        UserEntity user = user(LocalDateTime.of(2026, 11, 5, 3, 0));

        assertThat(cleanup.remaining(user)).contains(java.time.Duration.ofDays(1));
    }

    @Test
    void purgeDeletesOnlyTheReturnedRows() {
        when(accounts.findUnverifiedCreatedBefore(LocalDateTime.of(2026, 11, 4, 3, 0)))
                .thenReturn(List.of(account(4L), account(9L)));

        assertThat(cleanup.purgeExpired()).isEqualTo(2);
        verify(deleter).deleteIfUnverified(4L);
        verify(deleter).deleteIfUnverified(9L);
    }

    @Test
    void confirmLegacyActivatesAFreshAccount() {
        UserEntity user = user(LocalDateTime.of(2026, 11, 10, 3, 0));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L)));

        cleanup.confirmLegacy(7L);

        assertThat(user.isEnabled()).isTrue();
        verify(users).save(user);
        verify(deleter, never()).deleteIfUnverified(7L);
    }

    @Test
    void confirmLegacyDeletesAnExpiredAccount() {
        UserEntity user = user(null);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(accounts.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cleanup.confirmLegacy(7L)).isInstanceOf(InvalidSignupTokenException.class);
        verify(deleter).deleteIfUnverified(7L);
        verify(users, never()).save(user);
    }

    private static UserEntity user(LocalDateTime createdAt) {
        UserEntity user = UserEntity.builder().id(7L).enabled(false).build();
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        return user;
    }

    private static UserEmailAccountEntity account(long userId) {
        return UserEmailAccountEntity.builder()
                .userId(userId)
                .user(UserEntity.builder().id(userId).enabled(false).build())
                .email("person@gmail.com")
                .password("{noop}old")
                .build();
    }
}
