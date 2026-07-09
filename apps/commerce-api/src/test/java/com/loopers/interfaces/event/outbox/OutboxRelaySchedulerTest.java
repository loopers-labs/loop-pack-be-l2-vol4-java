package com.loopers.interfaces.event.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class OutboxRelaySchedulerTest {

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("Outbox Relay가 실행될 때,")
    @Nested
    class Relay {

        @DisplayName("PENDING 상태의 outbox 레코드를 Kafka로 발행하고 PUBLISHED로 변경한다.")
        @Test
        void relay_publishesPendingAndMarksPublished() {
            // arrange
            outboxJpaRepository.save(OutboxModel.create("catalog-events", "10", "LikedEvent", "{\"productId\":10}"));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            // act
            outboxRelayScheduler.relay();

            // assert
            var records = outboxJpaRepository.findAll();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            verify(kafkaTemplate, times(1)).send("catalog-events", "10", "{\"productId\":10}");
        }

        @DisplayName("Kafka 발행이 실패하면 outbox 레코드는 PENDING 상태로 유지된다.")
        @Test
        void relay_keepsPending_whenKafkaFails() {
            // arrange
            outboxJpaRepository.save(OutboxModel.create("catalog-events", "10", "LikedEvent", "{\"productId\":10}"));
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 연결 실패")));

            // act
            outboxRelayScheduler.relay();

            // assert
            var records = outboxJpaRepository.findAll();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        }
    }
}
