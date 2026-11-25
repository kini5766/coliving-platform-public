package com.coliving.platform.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 7일이 지난 미인증 가입 행을 지운다. 확인 전 데이터는 Redis 만료로도 사라진다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnverifiedSignupPurge {

    private final UnverifiedSignupCleanup unverifiedSignupCleanup;

    /** 한 시간에 한 번, 기한이 지난 미인증 행을 지운다. 기동 2분 뒤에 처음 돈다. */
    @Scheduled(fixedDelayString = "${app.signup.unverified-purge-interval:PT1H}", initialDelay = 120_000)
    public void purge() {
        int removed = unverifiedSignupCleanup.purgeExpired();
        if (removed > 0) {
            log.info("기한이 지난 미인증 가입 {}건을 삭제했습니다.", removed);
        }
    }
}
