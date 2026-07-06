package com.loopers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 이벤트 처리 설정.
 *
 * <p>{@code @Async} 리스너(예: 데이터 플랫폼 전송)를 요청 스레드와 분리해 실행한다.
 * 기본 {@code SimpleAsyncTaskExecutor}(요청마다 새 스레드)를 피하고, 경계가 있는 스레드풀을 사용한다.
 */
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-event-");
        executor.initialize();
        return executor;
    }
}
