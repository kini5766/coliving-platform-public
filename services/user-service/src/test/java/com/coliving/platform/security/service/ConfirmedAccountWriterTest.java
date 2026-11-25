package com.coliving.platform.security.service;

import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import com.coliving.platform.user.service.ContractAutocompleteService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmedAccountWriterTest {

    private final UserRepository users = mock(UserRepository.class);
    private final EmailAccountRepository accounts = mock(EmailAccountRepository.class);
    private final ContractAutocompleteService autocomplete = mock(ContractAutocompleteService.class);
    private final ConfirmedAccountWriter writer = new ConfirmedAccountWriter(users, accounts, autocomplete);

    @Test
    void insertsAnEnabledAccountWithTheGivenPasswordHash() {
        when(users.save(any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            assertThat(user.isEnabled()).isTrue();
            return UserEntity.builder().id(7L).enabled(true).roles(user.getRoles()).build();
        });

        writer.insert("person@gmail.com", "{noop}secret");

        verify(autocomplete).save(any(), eq("person@gmail.com"), isNull(), isNull(), isNull(), isNull());
        verify(accounts).saveAndFlush(org.mockito.ArgumentMatchers.argThat(account ->
                "person@gmail.com".equals(account.getEmail()) && "{noop}secret".equals(account.getPassword())));
    }

    @Test
    void duplicateInsertIsRejected() {
        when(users.save(any())).thenAnswer(invocation -> UserEntity.builder().id(7L).enabled(true).build());
        when(accounts.saveAndFlush(any(UserEmailAccountEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> writer.insert("person@gmail.com", "{noop}secret"))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }
}
