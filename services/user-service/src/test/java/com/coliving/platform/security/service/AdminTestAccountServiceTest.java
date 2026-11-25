package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.PendingSignupStore;
import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO;
import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.security.service.SignupService.InvalidSignupException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminTestAccountServiceTest {

    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PendingSignupStore pending = mock(PendingSignupStore.class);
    private final SignupEmailVerificationTokenDAO tokens = mock(SignupEmailVerificationTokenDAO.class);
    private final UnverifiedSignupCleanup cleanup = mock(UnverifiedSignupCleanup.class);
    private final ConfirmedAccountWriter writer = mock(ConfirmedAccountWriter.class);
    private final AdminTestAccountService service =
            new AdminTestAccountService(accounts, encoder, pending, tokens, cleanup, writer);

    @Test
    void createsAnActiveAccountWithoutMail() {
        when(accounts.findByEmail("person@test.com")).thenReturn(Optional.empty());
        when(encoder.encode("secret")).thenReturn("{noop}secret");

        service.create("  Person@Test.com ", "secret");

        verify(pending).delete("person@test.com");
        verify(tokens).revokeEmail("person@test.com");
        verify(writer).insert("person@test.com", "{noop}secret");
        verify(cleanup, never()).deleteUnverified(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(accounts.findByEmail("person@test.com")).thenReturn(Optional.of(account(true)));

        assertThatThrownBy(() -> service.create("person@test.com", "secret"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(writer, never()).insert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unverifiedEmailIsReplaced() {
        when(accounts.findByEmail("person@test.com")).thenReturn(Optional.of(account(false)));
        when(encoder.encode("secret")).thenReturn("{noop}secret");

        service.create("person@test.com", "secret");

        verify(cleanup).deleteUnverified(7L);
        verify(writer).insert("person@test.com", "{noop}secret");
    }

    @Test
    void blankPasswordIsRejected() {
        assertThatThrownBy(() -> service.create("person@test.com", " "))
                .isInstanceOf(InvalidSignupException.class);
        verifyNoInteractions(accounts, pending, tokens, cleanup, writer);
    }

    private static UserEmailAccountEntity account(boolean enabled) {
        UserEntity user = UserEntity.builder().id(7L).enabled(enabled).build();
        return UserEmailAccountEntity.builder()
                .userId(7L)
                .user(user)
                .email("person@test.com")
                .password("{noop}old")
                .build();
    }
}
