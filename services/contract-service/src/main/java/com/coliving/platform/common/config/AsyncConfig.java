package com.coliving.platform.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @Async를 켜는 스위치 + 메일 발송 전용 스레드 풀.
 * SMTP는 한 통에 수백 ms~수 초가 걸려서 요청/스케줄러 스레드와 분리한다 (ContractMailListener).
 *
 * @author 한성현
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String MAIL_EXECUTOR = "mailTaskExecutor";

    @Bean(name = MAIL_EXECUTOR)
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(500);           // D-day 알림처럼 한 번에 몰려도 버티게
        ex.setThreadNamePrefix("mail-");
        ex.setWaitForTasksToCompleteOnShutdown(true);   // 서버 내릴 때 큐에 남은 메일은 보내고 종료
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
}
