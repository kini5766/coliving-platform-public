package com.coliving.platform.user.service;

import com.coliving.platform.security.service.AuthService;
import com.coliving.platform.user.entity.ContractAutocompleteEntity;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.ContractAutocompleteRepository;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.SocialAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountWithdrawalServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailAccountRepository emailAccountRepository = mock(EmailAccountRepository.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final ContractAutocompleteRepository contractAutocompleteRepository = mock(ContractAutocompleteRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final AccountWithdrawalService service = new AccountWithdrawalService(
            userRepository,
            emailAccountRepository,
            socialAccountRepository,
            contractAutocompleteRepository,
            authService
    );

    @Test
    void withdrawDeletesAccountDataAndRevokesSessions() {
        UserEntity user = UserEntity.builder().id(7L).nickname("민수").enabled(true).build();
        when(emailAccountRepository.findById(7L)).thenReturn(Optional.of(mock(UserEmailAccountEntity.class)));
        when(contractAutocompleteRepository.findById(7L)).thenReturn(Optional.of(mock(ContractAutocompleteEntity.class)));

        service.withdraw(user, "current-session");

        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getNickname()).isNull();
        verify(userRepository).saveAndFlush(user);
        verify(emailAccountRepository).delete(org.mockito.ArgumentMatchers.any(UserEmailAccountEntity.class));
        verify(contractAutocompleteRepository).delete(org.mockito.ArgumentMatchers.any(ContractAutocompleteEntity.class));
        verify(socialAccountRepository).deleteByUserId(7L);
        verify(authService).revokeUser("7", "current-session");
    }

    @Test
    void withdrawWithoutLoginRowsStillDisablesAndRevokes() {
        UserEntity user = UserEntity.builder().id(7L).nickname("민수").enabled(true).build();
        when(emailAccountRepository.findById(7L)).thenReturn(Optional.empty());
        when(contractAutocompleteRepository.findById(7L)).thenReturn(Optional.empty());

        service.withdraw(user, null);

        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getNickname()).isNull();
        verify(emailAccountRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(contractAutocompleteRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(socialAccountRepository).deleteByUserId(7L);
        verify(authService).revokeUser("7", null);
    }
}
