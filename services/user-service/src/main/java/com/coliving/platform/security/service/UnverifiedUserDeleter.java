package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.repository.ContractAutocompleteRepository;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.SocialAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확인하지 않은 가입 행만 지운다. 활성 회원과 탈퇴 회원은 그대로 둔다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class UnverifiedUserDeleter {

    private final EmailAccountRepository emailAccountRepository;
    private final ContractAutocompleteRepository contractAutocompleteRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final SignupEmailVerificationTokenDAO signupEmailVerificationTokenDAO;

    /** 비활성 이메일 계정과 자동완성, 소셜 연결, 회원 행을 지운다. 활성 회원이면 그대로 둔다. 별도 트랜잭션이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteIfUnverified(Long userId) {
        if (userId == null) {
            return;
        }
        Optional<UserEmailAccountEntity> account = emailAccountRepository.findById(userId);
        if (account.isEmpty()) {
            signupEmailVerificationTokenDAO.revokeUser(userId);
            return;
        }
        if (account.get().getUser().isEnabled()) {
            return;
        }
        emailAccountRepository.delete(account.get());
        contractAutocompleteRepository.findById(userId).ifPresent(contractAutocompleteRepository::delete);
        socialAccountRepository.deleteByUserId(userId);
        emailAccountRepository.flush();
        userRepository.findById(userId).ifPresent(userRepository::delete);
        signupEmailVerificationTokenDAO.revokeUser(userId);
    }
}
