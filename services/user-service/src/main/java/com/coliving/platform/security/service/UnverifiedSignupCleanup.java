package com.coliving.platform.security.service;

import com.coliving.platform.security.dao.PendingSignupStore;
import com.coliving.platform.security.service.SignupService.InvalidSignupTokenException;
import com.coliving.platform.user.entity.UserEmailAccountEntity;
import com.coliving.platform.user.entity.UserEntity;
import com.coliving.platform.user.repository.EmailAccountRepository;
import com.coliving.platform.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확인 전 가입 행의 남은 기간과 삭제. Redis 대기는 다루지 않는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Service
@RequiredArgsConstructor
public class UnverifiedSignupCleanup {

    private final EmailAccountRepository emailAccountRepository;
    private final UserRepository userRepository;
    private final UnverifiedUserDeleter unverifiedUserDeleter;
    private final Clock clock;

    /** 처음 가입 시각부터 7일. 없으면 이미 지난 것으로 본다. */
    public Optional<Duration> remaining(UserEntity user) {
        LocalDateTime created = user.getCreatedAt();
        if (created == null) {
            return Optional.empty();
        }
        LocalDateTime deadline = created.plus(PendingSignupStore.RETENTION);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(deadline)) {
            return Optional.empty();
        }
        Duration left = Duration.between(now, deadline);
        if (left.isZero() || left.isNegative()) {
            return Optional.empty();
        }
        return Optional.of(left);
    }

    /** 확인하지 않은 가입 행을 지운다. 활성 회원은 지우지 않는다. */
    public void deleteUnverified(Long userId) {
        unverifiedUserDeleter.deleteIfUnverified(userId);
    }

    /** 만든 지 7일이 지난 미인증 행을 지우고 건수를 돌려준다. */
    public int purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(PendingSignupStore.RETENTION);
        var expired = emailAccountRepository.findUnverifiedCreatedBefore(cutoff);
        for (UserEmailAccountEntity account : expired) {
            unverifiedUserDeleter.deleteIfUnverified(account.getUserId());
        }
        return expired.size();
    }

    /** 예전에 비활성 회원으로 만들어 둔 가입을 확인한다. 7일이 지났으면 지운다. */
    @Transactional
    public void confirmLegacy(long userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(InvalidSignupTokenException::new);
        if (user.isEnabled()) {
            return;
        }
        if (emailAccountRepository.findById(userId).isEmpty() || remaining(user).isEmpty()) {
            unverifiedUserDeleter.deleteIfUnverified(userId);
            throw new InvalidSignupTokenException();
        }
        user.activate();
        userRepository.save(user);
    }
}
