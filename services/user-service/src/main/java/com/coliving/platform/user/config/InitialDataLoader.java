package com.coliving.platform.user.config;

import com.coliving.platform.user.entity.Role;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import com.coliving.platform.user.service.ContractAutocompleteService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * dev 프로파일에서만 로컬 관리자 admin@naver.com 이 없으면 ADMIN 으로 한 번 넣는다. 비밀번호는 12341234.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@Profile("dev")
@Component
public class InitialDataLoader implements CommandLineRunner {
    private final UserRepository memberRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContractAutocompleteService contractAutocompleteService;

    /** 시드에 필요한 저장소와 비밀번호 인코더를 받는다. */
    public InitialDataLoader(UserRepository memberRepository,
                              EmailAccountRepository emailAccountRepository,
                              PasswordEncoder passwordEncoder,
                              ContractAutocompleteService contractAutocompleteService) {
        this.memberRepository = memberRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.contractAutocompleteService = contractAutocompleteService;
    }

    /** 이미 있으면 아무것도 하지 않는다. */
    @Override
    public void run(String... args) {
        if (emailAccountRepository.findByEmail("admin@naver.com").isPresent()) {
            return;
        }
        UserEntity member = memberRepository.save(UserEntity.builder()
                .roles(Set.of(Role.ADMIN))
                .build());
        contractAutocompleteService.save(member, "admin@naver.com", null, null, null, null);
        emailAccountRepository.save(UserEmailAccountEntity.builder()
                .user(member)
                .email("admin@naver.com")
                .password(passwordEncoder.encode("12341234"))
                .build());
    }
}
