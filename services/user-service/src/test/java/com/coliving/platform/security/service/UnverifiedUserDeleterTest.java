package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.ContractAutocompleteRepository;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.SocialAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnverifiedUserDeleterTest {

    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final ContractAutocompleteRepository autocomplete = mock(ContractAutocompleteRepository.class);
    private final SocialAccountRepository social = mock(SocialAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SignupEmailVerificationTokenDAO tokens = mock(SignupEmailVerificationTokenDAO.class);
    private final UnverifiedUserDeleter deleter =
            new UnverifiedUserDeleter(accounts, autocomplete, social, users, tokens);

    @Test
    void deletesADisabledEmailAccount() {
        UserEntity user = UserEntity.builder().id(7L).enabled(false).build();
        UserEmailAccountEntity account = UserEmailAccountEntity.builder()
                .userId(7L)
                .user(user)
                .email("person@gmail.com")
                .password("{noop}old")
                .build();
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(autocomplete.findById(7L)).thenReturn(Optional.empty());
        when(users.findById(7L)).thenReturn(Optional.of(user));

        deleter.deleteIfUnverified(7L);

        verify(accounts).delete(account);
        verify(social).deleteByUserId(7L);
        verify(users).delete(user);
        verify(tokens).revokeUser(7L);
    }

    @Test
    void leavesAnEnabledAccount() {
        UserEntity user = UserEntity.builder().id(7L).enabled(true).build();
        UserEmailAccountEntity account = UserEmailAccountEntity.builder()
                .userId(7L)
                .user(user)
                .email("person@gmail.com")
                .password("{noop}old")
                .build();
        when(accounts.findById(7L)).thenReturn(Optional.of(account));

        deleter.deleteIfUnverified(7L);

        verify(users, never()).delete(user);
        verify(tokens, never()).revokeUser(7L);
    }
}
