package com.coliving.platform.security.service;

import com.coliving.platform.common.config.MailProperties;
import com.coliving.platform.security.dao.PendingSignupStore;
import com.coliving.platform.security.dao.PendingSignupStore.PendingSignup;
import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO;
import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO.LegacyUser;
import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO.PendingEmail;
import com.coliving.platform.security.mail.SignupVerificationMailSender;
import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.security.service.SignupService.InvalidSignupException;
import com.coliving.platform.security.service.SignupService.InvalidSignupTokenException;
import com.coliving.platform.security.service.SignupService.MailDisabledException;
import com.coliving.platform.security.service.SignupService.MailSendException;
import com.coliving.platform.security.service.SignupService.TooManyRequestsException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SignupServiceTest {

    private static final String EMAIL = "person@gmail.com";
    private static final String IP = "203.0.113.5";

    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SignupEmailVerificationTokenDAO tokens = mock(SignupEmailVerificationTokenDAO.class);
    private final SignupVerificationMailSender mail = mock(SignupVerificationMailSender.class);
    private final PendingSignupStore pending = mock(PendingSignupStore.class);
    private final SignupMailRateLimiter limiter = mock(SignupMailRateLimiter.class);
    private final UnverifiedSignupCleanup cleanup = mock(UnverifiedSignupCleanup.class);
    private final SignupAccountWriter writer = mock(SignupAccountWriter.class);

    @BeforeEach
    void defaults() {
        when(limiter.allow(any(), any())).thenReturn(true);
        when(accounts.findByEmail(any())).thenReturn(Optional.empty());
        when(pending.remaining(any())).thenReturn(Optional.empty());
        when(pending.find(any())).thenReturn(Optional.empty());
    }

    @Test
    void mailDisabledRejectsBeforeLookup() {
        SignupService service = service(false);

        assertThatThrownBy(() -> service.signup(EMAIL, "secret", IP)).isInstanceOf(MailDisabledException.class);
        verifyNoInteractions(accounts, encoder, tokens, mail, pending, limiter, cleanup, writer);
    }

    @Test
    void signupKeepsTheAddressPendingUntilConfirmation() {
        SignupService service = service(true);
        when(encoder.encode("secret")).thenReturn("{noop}secret");
        when(tokens.issue(EMAIL)).thenReturn("raw-token");

        service.signup("  Person@Gmail.com ", "secret", IP);

        verify(pending).save(EMAIL, "{noop}secret", PendingSignupStore.RETENTION);
        verify(mail).send(EMAIL, "http://localhost:5173/signup/confirm?token=raw-token");
        verifyNoInteractions(writer);
        verify(cleanup, never()).deleteUnverified(any());
    }

    @Test
    void mailSendFailureDropsANewPendingSignup() {
        SignupService service = service(true);
        when(encoder.encode("secret")).thenReturn("{noop}secret");
        when(tokens.issue(EMAIL)).thenReturn("raw-token");
        doThrow(new IllegalStateException("smtp"))
                .when(mail)
                .send(EMAIL, "http://localhost:5173/signup/confirm?token=raw-token");

        assertThatThrownBy(() -> service.signup(EMAIL, "secret", IP)).isInstanceOf(MailSendException.class);
        verify(tokens).revokeEmail(EMAIL);
        verify(pending).delete(EMAIL);
        verifyNoInteractions(writer);
    }

    @Test
    void signupAgainKeepsTheOriginalWindow() {
        SignupService service = service(true);
        when(pending.remaining(EMAIL)).thenReturn(Optional.of(Duration.ofDays(2)));
        when(encoder.encode("secret")).thenReturn("{noop}secret");
        when(tokens.issue(EMAIL)).thenReturn("raw-token");

        service.signup(EMAIL, "secret", IP);

        verify(pending).save(EMAIL, "{noop}secret", Duration.ofDays(2));
        verify(pending, never()).delete(any());
        verify(mail).send(EMAIL, "http://localhost:5173/signup/confirm?token=raw-token");
    }

    @Test
    void activeEmailIsRejected() {
        SignupService service = service(true);
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account(true)));

        assertThatThrownBy(() -> service.signup(EMAIL, "secret", IP)).isInstanceOf(EmailAlreadyUsedException.class);
        verify(pending).delete(EMAIL);
        verify(tokens).revokeEmail(EMAIL);
        verifyNoInteractions(mail, writer);
    }

    @Test
    void expiredUnverifiedAccountIsDeletedAndStartedAgain() {
        SignupService service = service(true);
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account(false)));
        when(cleanup.remaining(any())).thenReturn(Optional.empty());
        when(encoder.encode("secret")).thenReturn("{noop}secret");
        when(tokens.issue(EMAIL)).thenReturn("raw-token");

        service.signup(EMAIL, "secret", IP);

        verify(cleanup).deleteUnverified(7L);
        verify(pending).save(EMAIL, "{noop}secret", PendingSignupStore.RETENTION);
    }

    @Test
    void freshUnverifiedAccountKeepsTheRemainingTime() {
        SignupService service = service(true);
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account(false)));
        when(cleanup.remaining(any())).thenReturn(Optional.of(Duration.ofDays(3)));
        when(encoder.encode("secret")).thenReturn("{noop}secret");
        when(tokens.issue(EMAIL)).thenReturn("raw-token");

        service.signup(EMAIL, "secret", IP);

        verify(cleanup).deleteUnverified(7L);
        verify(pending).save(EMAIL, "{noop}secret", Duration.ofDays(3));
        verify(mail).send(eq(EMAIL), any());
    }

    @Test
    void tooManyRequestsStopsBeforeLookup() {
        SignupService service = service(true);
        when(limiter.allow(EMAIL, IP)).thenReturn(false);

        assertThatThrownBy(() -> service.signup(EMAIL, "secret", IP)).isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(accounts, mail, pending, writer);
    }

    @Test
    void blankPasswordIsRejected() {
        SignupService service = service(true);

        assertThatThrownBy(() -> service.signup(EMAIL, " ", IP)).isInstanceOf(InvalidSignupException.class);
        verifyNoInteractions(accounts, limiter, mail, pending);
    }

    @Test
    void resendSendsWhenASignupIsWaiting() {
        SignupService service = service(true);
        when(pending.find(EMAIL)).thenReturn(Optional.of(new PendingSignup(EMAIL, "{noop}secret")));
        when(tokens.issue(EMAIL)).thenReturn("raw-token");

        service.resend(EMAIL, IP);

        verify(mail).send(EMAIL, "http://localhost:5173/signup/confirm?token=raw-token");
        verify(accounts, never()).findByEmail(any());
    }

    @Test
    void resendWithoutAWaitingSignupDoesNotSend() {
        SignupService service = service(true);

        service.resend(EMAIL, IP);

        verifyNoInteractions(mail, writer);
    }

    @Test
    void resendMovesAFreshUnverifiedAccountOutOfTheMemberTable() {
        SignupService service = service(true);
        when(pending.find(EMAIL)).thenReturn(Optional.empty(), Optional.of(new PendingSignup(EMAIL, "{noop}old")));
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(account(false)));
        when(cleanup.remaining(any())).thenReturn(Optional.of(Duration.ofDays(3)));
        when(tokens.issue(EMAIL)).thenReturn("raw-token");

        service.resend(EMAIL, IP);

        verify(pending).save(EMAIL, "{noop}old", Duration.ofDays(3));
        verify(cleanup).deleteUnverified(7L);
        verify(mail).send(eq(EMAIL), any());
        verifyNoInteractions(encoder);
    }

    @Test
    void resendOverTheLimitDoesNotLookUp() {
        SignupService service = service(true);
        when(limiter.allow(EMAIL, IP)).thenReturn(false);

        assertThatThrownBy(() -> service.resend(EMAIL, IP)).isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(accounts, pending, mail);
    }

    @Test
    void confirmCreatesAnActiveAccount() {
        SignupService service = service(true);
        when(tokens.lookup("raw-token")).thenReturn(Optional.of(new PendingEmail("abc")));
        when(pending.findByEmailHash("abc")).thenReturn(Optional.of(new PendingSignup(EMAIL, "{noop}secret")));

        service.confirmEmail("raw-token");

        verify(writer).create(EMAIL, "{noop}secret");
        verify(tokens).consume("raw-token");
        verify(pending).delete(EMAIL);
    }

    @Test
    void confirmActivatesALegacyAccount() {
        SignupService service = service(true);
        when(tokens.lookup("raw-token")).thenReturn(Optional.of(new LegacyUser(7L)));

        service.confirmEmail("raw-token");

        verify(cleanup).confirmLegacy(7L);
        verify(tokens).consume("raw-token");
        verifyNoInteractions(writer);
    }

    @Test
    void confirmRejectsAnUnknownToken() {
        SignupService service = service(true);
        when(tokens.lookup("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmEmail("missing")).isInstanceOf(InvalidSignupTokenException.class);
        verify(tokens, never()).consume(any());
    }

    @Test
    void confirmDropsATokenWhosePendingSignupIsGone() {
        SignupService service = service(true);
        when(tokens.lookup("raw-token")).thenReturn(Optional.of(new PendingEmail("abc")));
        when(pending.findByEmailHash("abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmEmail("raw-token")).isInstanceOf(InvalidSignupTokenException.class);
        verify(tokens).consume("raw-token");
        verifyNoInteractions(writer);
    }

    @Test
    void confirmOfATakenEmailInvalidatesTheLink() {
        SignupService service = service(true);
        when(tokens.lookup("raw-token")).thenReturn(Optional.of(new PendingEmail("abc")));
        when(pending.findByEmailHash("abc")).thenReturn(Optional.of(new PendingSignup(EMAIL, "{noop}secret")));
        doThrow(new EmailAlreadyUsedException()).when(writer).create(EMAIL, "{noop}secret");

        assertThatThrownBy(() -> service.confirmEmail("raw-token")).isInstanceOf(InvalidSignupTokenException.class);
        verify(tokens).consume("raw-token");
        verify(pending).delete(EMAIL);
    }

    private SignupService service(boolean mailEnabled) {
        MailProperties properties = new MailProperties(
                mailEnabled, "roomlink@gmail.com", "Roomlink", "http://localhost:5173", Duration.ofMinutes(30));
        return new SignupService(
                accounts, encoder, properties, tokens, mail, pending, limiter, cleanup, writer);
    }

    private static UserEmailAccountEntity account(boolean enabled) {
        UserEntity user = UserEntity.builder().id(7L).enabled(enabled).build();
        return UserEmailAccountEntity.builder()
                .userId(7L)
                .user(user)
                .email(EMAIL)
                .password("{noop}old")
                .build();
    }
}
