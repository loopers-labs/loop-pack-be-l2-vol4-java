package com.loopers.application.eventpublish;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    /**
     * 유저 행동 로깅 / AFTER_COMMIT 부가 로직 전용 풀.
     * <p>
     * 응답 지연에 포함되지 않아야 하므로 API 요청 스레드와 분리한다.
     * 큐가 넘치면 caller 스레드에서 실행 — 유실 방지.
     */
    @Bean(name = "applicationEventExecutor")
    public Executor applicationEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("app-event-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
