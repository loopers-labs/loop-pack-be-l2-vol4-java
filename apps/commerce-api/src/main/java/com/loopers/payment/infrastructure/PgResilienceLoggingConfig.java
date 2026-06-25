package com.loopers.payment.infrastructure;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모든 Retry 인스턴스에 재시도 로깅 리스너를 한 번씩 붙이는 글로벌 이벤트 컨슈머. 실행 경로(PgResilienceExecutor)에
 * 로그를 섞지 않고 resilience4j 이벤트 훅으로 분리한다. 재시도 횟수 집계는 Micrometer 메트릭이 별도로 노출한다.
 *
 * <p>이벤트엔 orderNumber 가 없어(요청 본문에 있음) instance/attempt/cause 만 남긴다. "어느 주문이 재시도됐나"는
 * 정합성 보정·정산이 orderId 로 PG 거래 수를 대조해 잡는다(read 타임아웃 재시도 시 이중 결제 가능).
 */
@Slf4j
@Configuration
public class PgResilienceLoggingConfig {

    @Bean
    public RegistryEventConsumer<Retry> pgRetryEventLogger() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> event) {
                attachRetryLogger(event.getAddedEntry());
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> event) {
                attachRetryLogger(event.getNewEntry());
            }
        };
    }

    static void attachRetryLogger(Retry retry) {
        retry.getEventPublisher().onRetry(event -> log.warn(
                "PG 호출 재시도 instance={} attempt={} cause={}",
                event.getName(), event.getNumberOfRetryAttempts(),
                event.getLastThrowable() == null ? null : event.getLastThrowable().toString()));
    }
}
