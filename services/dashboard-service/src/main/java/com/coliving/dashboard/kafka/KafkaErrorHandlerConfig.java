package com.coliving.dashboard.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 컨슈머가 예외를 던졌을 때의 재시도 방식.
 * ES가 잠깐 느려지거나 끊겼을 때 결제·환불 집계가 빠지지 않도록, 1초 간격으로 5번 더 시도한다.
 * 모두 실패하면 에러 로그를 남기고 그 메시지를 건너뛴다.
 */
@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 5L));
    }
}