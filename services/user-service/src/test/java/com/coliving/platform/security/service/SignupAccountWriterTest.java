package com.coliving.platform.security.service;

import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignupAccountWriterTest {

    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final UnverifiedUserDeleter deleter = mock(UnverifiedUserDeleter.class);
    private final ConfirmedAccountWriter confirmed = mock(ConfirmedAccountWriter.class);
    private final SignupAccountWriter writer = new SignupAccountWriter(accounts, deleter, confirmed);

    @Test
    void createsWhenTheEmailIsFree() {
        when(accounts.findByEmail("person@gmail.com")).thenReturn(Optional.empty());

        writer.create("person@gmail.com", "{noop}secret");

        verify(confirmed).insert("person@gmail.com", "{noop}secret");
        verify(deleter, never()).deleteIfUnverified(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void replacesAnUnverifiedRow() {
        when(accounts.findByEmail("person@gmail.com")).thenReturn(Optional.of(account(false)));

        writer.create("person@gmail.com", "{noop}secret");

        verify(deleter).deleteIfUnverified(7L);
        verify(confirmed).insert("person@gmail.com", "{noop}secret");
    }

    @Test
    void rejectsAnActiveAccount() {
        when(accounts.findByEmail("person@gmail.com")).thenReturn(Optional.of(account(true)));

        assertThatThrownBy(() -> writer.create("person@gmail.com", "{noop}secret"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(confirmed, never()).insert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static UserEmailAccountEntity account(boolean enabled) {
        UserEntity user = UserEntity.builder().id(7L).enabled(enabled).build();
        return UserEmailAccountEntity.builder()
                .userId(7L)
                .user(user)
                .email("person@gmail.com")
                .password("{noop}old")
                .build();
    }
}
