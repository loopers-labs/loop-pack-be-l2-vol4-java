package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxRelayIntegrationTest {

    private final OutboxRelay outboxRelay;
    private final OutboxJpaRepository outboxJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    // 실제 브로커 대신 발행 결과를 제어한다 — Relay 의 "발행 확인 후에만 마킹" 제어 흐름을 검증하기 위함.
    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    public OutboxRelayIntegrationTest(
        OutboxRelay outboxRelay,
        OutboxJpaRepository outboxJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.outboxRelay = outboxRelay;
        this.outboxJpaRepository = outboxJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("미발행 outbox 행들을 catalog-events 토픽으로 발행하고, published=true 로 마킹한다.")
    @Test
    void publishesAndMarks_whenUnpublishedRowsExist() {
        // given
        outboxJpaRepository.save(OutboxEvent.of("Product", 1L, "LIKED", "evt-1", "{\"productId\":1,\"type\":\"LIKED\"}"));
        outboxJpaRepository.save(OutboxEvent.of("Product", 2L, "LIKED", "evt-2", "{\"productId\":2,\"type\":\"LIKED\"}"));
        CompletableFuture<SendResult<Object, Object>> delivered = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(anyString(), any(), any())).willReturn(delivered);

        // when
        outboxRelay.relay();

        // then
        ArgumentCaptor<OutboxMessage> sent = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(kafkaTemplate, times(2)).send(eq("catalog-events"), any(), sent.capture());
        assertAll(
            () -> assertThat(sent.getAllValues()).extracting(OutboxMessage::eventId).containsExactlyInAnyOrder("evt-1", "evt-2"),
            () -> assertThat(outboxJpaRepository.findAll()).allMatch(OutboxEvent::isPublished)
        );
    }

    @DisplayName("발행에 실패하면 published=false 로 남겨 다음 폴링에 재발행한다 — At Least Once.")
    @Test
    void leavesUnpublished_whenSendFails() {
        // given
        outboxJpaRepository.save(OutboxEvent.of("Product", 1L, "LIKED", "evt-1", "{\"productId\":1,\"type\":\"LIKED\"}"));
        CompletableFuture<SendResult<Object, Object>> failed =
            CompletableFuture.failedFuture(new RuntimeException("broker down"));
        given(kafkaTemplate.send(anyString(), any(), any())).willReturn(failed);

        // when
        outboxRelay.relay();

        // then
        verify(kafkaTemplate).send(eq("catalog-events"), any(), any());
        assertThat(outboxJpaRepository.findAll()).noneMatch(OutboxEvent::isPublished);
    }
}
