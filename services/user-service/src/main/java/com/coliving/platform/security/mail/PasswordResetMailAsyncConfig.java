package com.coliving.platform.security.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 재설정 메일 전용 실행기. 코어 1, 최대 2, 대기 50.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Configuration
@EnableAsync
public class PasswordResetMailAsyncConfig {

    /** passwordResetMailExecutor 스레드 풀. */
    @Bean(name = PasswordResetMailDispatcher.EXECUTOR)
    public ThreadPoolTaskExecutor passwordResetMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("password-reset-mail-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        return executor;
    }
}
