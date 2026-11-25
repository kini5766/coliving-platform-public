package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.PendingSignupStore;
import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO;
import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.security.service.SignupService.InvalidSignupException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 관리자가 확인 메일 없이 바로 로그인되는 계정을 만든다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class AdminTestAccountService {

    private final EmailAccountRepository emailAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PendingSignupStore pendingSignups;
    private final SignupEmailVerificationTokenDAO signupEmailVerificationTokenDAO;
    private final UnverifiedSignupCleanup unverifiedSignupCleanup;
    private final ConfirmedAccountWriter confirmedAccountWriter;

    /** 대기 가입과 미인증 행을 지우고 활성 계정을 만든다. 활성 이메일이면 EmailAlreadyUsedException. */
    public void create(String email, String password) {
        String normalized = SignupService.normalizeEmail(email);
        if (!isEmail(normalized) || password == null || password.isBlank()) {
            throw new InvalidSignupException();
        }
        pendingSignups.delete(normalized);
        signupEmailVerificationTokenDAO.revokeEmail(normalized);
        Optional<UserEmailAccountEntity> existing = emailAccountRepository.findByEmail(normalized);
        if (existing.isPresent()) {
            if (existing.get().getUser().isEnabled()) {
                throw new EmailAlreadyUsedException();
            }
            unverifiedSignupCleanup.deleteUnverified(existing.get().getUser().getId());
        }
        confirmedAccountWriter.insert(normalized, passwordEncoder.encode(password));
    }

    /** @ 와 도메인 점이 있는지만 본다. */
    private static boolean isEmail(String email) {
        int at = email.indexOf('@');
        return at > 0
                && email.indexOf('.', at + 1) > at + 1
                && !email.contains(" ")
                && !email.contains("\n")
                && !email.contains("\r");
    }
}
