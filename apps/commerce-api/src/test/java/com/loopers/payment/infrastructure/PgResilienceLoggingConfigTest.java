package com.loopers.payment.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class PgResilienceLoggingConfigTest {

    @Test
    @DisplayName("재시도가 일어나면 instance·attempt 와 함께 WARN 로그를 남긴다")
    void givenRetryLoggerAttached_whenRetry_thenLogsWarn() {
        Logger logger = (Logger) LoggerFactory.getLogger(PgResilienceLoggingConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        Retry retry = Retry.of("toss", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(RuntimeException.class)
                .build());
        PgResilienceLoggingConfig.attachRetryLogger(retry);

        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> flaky = () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
            return "ok";
        };

        try {
            Retry.decorateSupplier(retry, flaky).get();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("instance=toss")
                        && e.getFormattedMessage().contains("attempt=1"));
    }
}
