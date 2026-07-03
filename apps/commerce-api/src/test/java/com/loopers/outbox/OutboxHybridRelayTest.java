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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HYBRID 모드: 폴 주기를 기다리지 않고 커밋 직후 즉시 발행된다.
 * 폴러는 test 프로파일에서 600000ms 라 이 테스트 동안 돌 수 없으므로, 발행이 보이면 "즉시 발행"이 유일한 원인이다.
 */
@SpringBootTest
@TestPropertySource(properties = "outbox.relay.mode=HYBRID")
class OutboxHybridRelayTest {

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
    OutboxHybridRelayTest(ApplicationEventPublisher eventPublisher,
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
    @DisplayName("HYBRID: 주문 커밋 직후 폴러를 기다리지 않고 즉시 발행된다")
    void givenHybrid_whenCommitted_thenPublishedImmediately() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(sampleEvent()));

        assertThat(publisher.payloads()).hasSize(1);
    }
}
