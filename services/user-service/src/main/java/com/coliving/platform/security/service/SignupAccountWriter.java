package com.coliving.platform.security.service;

import com.coliving.platform.security.service.SignupService.EmailAlreadyUsedException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 확인된 가입을 활성 회원으로 저장한다.
 * 미인증 행 삭제는 별도 트랜잭션이라, 이어지는 저장이 실패해도 활성 회원을 되살리지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class SignupAccountWriter {

    private final EmailAccountRepository emailAccountRepository;
    private final UnverifiedUserDeleter unverifiedUserDeleter;
    private final ConfirmedAccountWriter confirmedAccountWriter;

    /** 활성 이메일이면 EmailAlreadyUsedException. 미인증 행은 지운 뒤 활성 회원을 만든다. */
    public void create(String email, String passwordHash) {
        Optional<UserEmailAccountEntity> existing = emailAccountRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getUser().isEnabled()) {
                throw new EmailAlreadyUsedException();
            }
            unverifiedUserDeleter.deleteIfUnverified(existing.get().getUser().getId());
        }
        confirmedAccountWriter.insert(email, passwordHash);
    }
}
