package com.coliving.platform.security.service;

import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.user.entity.Role;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import com.coliving.platform.user.service.ContractAutocompleteService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 정리된 이메일로 활성 회원을 만든다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class ConfirmedAccountWriter {

    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final ContractAutocompleteService contractAutocompleteService;

    /** 활성 회원과 이메일 계정, 계약 자동완성을 만든다. 이메일이 겹치면 EmailAlreadyUsedException. */
    @Transactional
    public void insert(String email, String passwordHash) {
        UserEntity user = userRepository.save(UserEntity.builder()
                .roles(Set.of(Role.defaultAccountRole()))
                .enabled(true)
                .build());
        contractAutocompleteService.save(user, email, null, null, null, null);
        try {
            emailAccountRepository.saveAndFlush(UserEmailAccountEntity.builder()
                    .user(user)
                    .email(email)
                    .password(passwordHash)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyUsedException();
        }
    }
}
