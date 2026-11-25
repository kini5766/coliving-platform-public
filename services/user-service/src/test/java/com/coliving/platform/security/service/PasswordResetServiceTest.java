package com.coliving.platform.security.service;

import com.coliving.platform.common.config.MailProperties;
import com.coliving.platform.security.dao.PasswordResetTokenDAO;
import com.coliving.platform.security.mail.PasswordResetMailDispatcher;
import com.coliving.platform.security.service.PasswordResetService.InvalidPasswordResetException;
import com.coliving.platform.security.service.PasswordResetService.InvalidPasswordResetTokenException;
import com.coliving.platform.security.service.PasswordResetService.MailDisabledException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final String IP = "203.0.113.5";

    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final PasswordResetTokenDAO tokens = mock(PasswordResetTokenDAO.class);
    private final PasswordResetMailDispatcher mail = mock(PasswordResetMailDispatcher.class);
    private final PasswordResetRateLimiter limits = mock(PasswordResetRateLimiter.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthService auth = mock(AuthService.class);

    @Test
    void mailDisabledDoesNotLookUpOrSend() {
        PasswordResetService service = service(false);

        assertThatThrownBy(() -> service.requestReset("resident-a@test.com", IP))
                .isInstanceOf(MailDisabledException.class);
        verifyNoInteractions(accounts, tokens, mail, limits);
    }

    @Test
    void mailEnabledSchedulesTheLinkAndDoesNotWaitForIt() {
        PasswordResetService service = service(true);
        when(limits.allow("resident-a@test.com", IP)).thenReturn(true);
        when(accounts.findByEmail("resident-a@test.com")).thenReturn(Optional.of(account()));
        when(tokens.issue(7L)).thenReturn("raw-token");

        service.requestReset("  Resident-A@test.com ", IP);

        verify(mail).send(
                7L,
                "resident-a@test.com",
                "http://localhost:5173/password-reset?token=raw-token");
    }

    @Test
    void unknownEmailDoesNotSend() {
        PasswordResetService service = service(true);
        when(limits.allow("missing@naver.com", IP)).thenReturn(true);
        when(accounts.findByEmail("missing@naver.com")).thenReturn(Optional.empty());

        service.requestReset("missing@naver.com", IP);

        verifyNoInteractions(tokens, mail);
    }

    @Test
    void overLimitMatchesAnUnknownEmail() {
        PasswordResetService service = service(true);
        when(limits.allow("resident-a@test.com", IP)).thenReturn(false);

        service.requestReset("resident-a@test.com", IP);

        verifyNoInteractions(accounts, tokens, mail);
    }

    @Test
    void scheduleFailureRevokesTheTokenAndDoesNotThrow() {
        PasswordResetService service = service(true);
        when(limits.allow("resident-a@test.com", IP)).thenReturn(true);
        when(accounts.findByEmail("resident-a@test.com")).thenReturn(Optional.of(account()));
        when(tokens.issue(7L)).thenReturn("raw-token");
        doThrow(new RejectedExecutionException("full"))
                .when(mail)
                .send(7L, "resident-a@test.com", "http://localhost:5173/password-reset?token=raw-token");

        service.requestReset("resident-a@test.com", IP);

        verify(tokens).revoke(7L);
    }

    @Test
    void confirmReplacesPasswordAndRevokesSessions() {
        PasswordResetService service = service(true);
        UserEmailAccountEntity account = account();
        when(tokens.consume("raw-token")).thenReturn(Optional.of(7L));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(encoder.encode("new-secret")).thenReturn("{noop}new-secret");

        service.confirm("raw-token", "new-secret");

        assertThat(account.getPassword()).isEqualTo("{noop}new-secret");
        verify(accounts).save(account);
        verify(auth).revokeUser("7", null);
    }

    @Test
    void confirmRejectsAUsedToken() {
        PasswordResetService service = service(true);
        when(tokens.consume("used")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("used", "new-secret"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        verify(encoder, never()).encode(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blankPasswordIsRejected() {
        PasswordResetService service = service(true);

        assertThatThrownBy(() -> service.confirm("raw-token", " "))
                .isInstanceOf(InvalidPasswordResetException.class);
        verifyNoInteractions(tokens, accounts, encoder, auth);
    }

    private PasswordResetService service(boolean enabled) {
        MailProperties mailProperties = new MailProperties(
                enabled, "roomlink@gmail.com", "Roomlink", "http://localhost:5173", Duration.ofMinutes(30));
        return new PasswordResetService(mailProperties, accounts, tokens, mail, limits, encoder, auth);
    }

    private static UserEmailAccountEntity account() {
        return UserEmailAccountEntity.builder()
                .userId(7L)
                .email("resident-a@test.com")
                .password("{noop}old")
                .build();
    }
}
