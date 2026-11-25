package com.coliving.platform.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄 작업을 켠다. 시계는 테스트가 바꿀 수 있게 빈으로 둔다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** 서버 기본 시간대. 테스트는 이 빈을 바꾼다. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
