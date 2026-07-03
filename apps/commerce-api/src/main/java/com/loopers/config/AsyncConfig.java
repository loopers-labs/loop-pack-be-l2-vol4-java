package com.loopers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 좋아요 등 도메인 이벤트 리스너를 요청 스레드와 분리해 실행하는 전용 executor.
     * <p>
     * 단일 스레드(core=max=1)로 둔다. 같은 상품에 좋아요→취소가 연달아 발생하면 +1/-1 이벤트가
     * 순서대로 처리돼야 하는데, 멀티 스레드면 순서가 뒤집혀 집계가 어긋날 수 있기 때문이다.
     * FIFO 큐 + 단일 소비자로 발행 순서를 보존한다. (Step 2에서 Kafka 파티션 키 기반 순서 보장으로 확장된다.)
     * <p>
     * 커넥션도 이 스레드에서 한 번에 하나만 사용하므로, 동기 AFTER_COMMIT에서 발생하던 커넥션 2배 점유
     * (요청 커넥션 + REQUIRES_NEW 커넥션)로 인한 풀 self-deadlock이 사라진다.
     */
    @Bean(name = "likeEventExecutor")
    public ThreadPoolTaskExecutor likeEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("like-event-");
        executor.initialize();
        return executor;
    }
}
