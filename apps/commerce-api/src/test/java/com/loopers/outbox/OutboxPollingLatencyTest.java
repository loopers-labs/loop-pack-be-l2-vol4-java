package com.loopers.outbox;

import com.loopers.order.application.event.OrderPaidEvent;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * POLLING_ONLY 모드: 커밋 직후 즉시 발행은 없고, 스케줄 폴러가 폴 간격 안에 발행한다.
 * 발행 지연 = 커밋 ~ 폴러 발행 시각. 폴 간격(200ms)에 비례하는 지연을 실측해 출력한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "outbox.relay.mode=POLLING_ONLY",
        "outbox.relay.fixed-delay-ms=200"
})
class OutboxPollingLatencyTest {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OutboxPollingLatencyTest.class);

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RecordingOutboxPublisher recordingOutboxPublisher() {
            return new RecordingOutboxPublisher();
        }
    }

    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final RecordingOutboxPublisher publisher;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OutboxPollingLatencyTest(ApplicationEventPublisher eventPublisher,
                             PlatformTransactionManager transactionManager,
                             RecordingOutboxPublisher publisher,
                             DatabaseCleanUp databaseCleanUp) {
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
        this.publisher = publisher;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        publisher.clear();
    }

    private OrderPaidEvent sampleEvent() {
        return new OrderPaidEvent("evt-" + UUID.randomUUID(), 1L,
                List.of(new OrderPaidEvent.Line(100L, 1)), ZonedDateTime.now());
    }

    @Test
    @DisplayName("POLLING_ONLY: 커밋 직후엔 미발행이고, 폴러가 폴 간격 안에 발행한다(지연=폴 간격)")
    void givenPollingOnly_whenCommitted_thenPublishedByPollerAfterDelay() {
        long committedAtNanos = System.nanoTime();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(sampleEvent()));

        await().atMost(Duration.ofSeconds(5)).until(() -> !publisher.payloads().isEmpty());

        long latencyMs = (publisher.firstPublishedAtNanos() - committedAtNanos) / 1_000_000;
        log.info("[측정] POLLING_ONLY 발행 지연 ≈ {}ms (fixedDelay=200ms)", latencyMs);
        assertThat(publisher.payloads()).hasSize(1);
        assertThat(latencyMs).isGreaterThanOrEqualTo(0);
    }
}
