package com.loopers.interfaces.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 리스너(@Async)용 스레드 풀 설정.
 *
 * <p>알림처럼 외부 I/O 가 끼는 부가 로직을 요청 스레드에서 떼어내기 위한 별도 풀이다. 바운드 큐를 둬
 * 폭주 시 무한 적재(OOM)를 막고, 큐가 가득 차면 호출 스레드에서 실행(CallerRuns)해 백프레셔를 건다.
 * (MDC/SecurityContext 전파가 필요해지면 TaskDecorator 를 추가한다 — 현재 알림 stub 은 불필요.)</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 좋아요 집계(likeCount) 전용 풀. 알림 풀과 분리해 한쪽 폭주가 다른 쪽을 막지 않게 한다.
     *
     * <p>집계를 좋아요 트랜잭션에서 떼어내(@Async + AFTER_COMMIT) "집계 실패 ≠ 좋아요 실패" 를 만든다.
     * 동기 AFTER_COMMIT + REQUIRES_NEW 는 원 커넥션을 쥔 채 새 커넥션을 잡아 커넥션 압박을 2배로 만들지만(실측 실패),
     * @Async 는 작업을 풀에 넘기고 원 커넥션을 즉시 놔줘 그 문제를 피한다. 대가는 eventual(lag)·best-effort 수렴이며,
     * 보장된 수렴은 Step 2(Outbox+Kafka)가 담당한다. CallerRuns 로 큐 포화 시 백프레셔를 건다.</p>
     */
    @Bean
    public Executor likeCountExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("like-agg-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
