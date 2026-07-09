package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Nested;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherSchedulerTest {

    @InjectMocks
    private OutboxEventPublisherScheduler scheduler;

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<Object, Object> kafkaTemplate;

    private OutboxEventModel createPendingEvent() {
        OutboxEventModel event = new OutboxEventModel(
            "11111111-1111-1111-1111-111111111111", "catalog-events", "10", "{\"eventType\":\"PRODUCT_LIKED\"}");
        ReflectionTestUtils.setField(event, "id", 1L);
        return event;
    }

    @DisplayName("publishPendingEvents()를 호출할 때,")
    @Nested
    class PublishPendingEvents {

        @DisplayName("Kafka 발행에 성공하면 해당 이벤트가 PUBLISHED로 전환된다.")
        @Test
        void marksPublished_whenKafkaSendSucceeds() {
            // arrange
            OutboxEventModel event = createPendingEvent();
            given(outboxEventRepository.findPending(anyInt())).willReturn(List.of(event));
            given(kafkaTemplate.send("catalog-events", "10", event.getPayload()))
                .willReturn(CompletableFuture.completedFuture(null));
            given(outboxEventRepository.findById(1L)).willReturn(Optional.of(event));

            // act
            scheduler.publishPendingEvents();

            // assert
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        }

        @DisplayName("Kafka 발행에 실패하면 해당 이벤트는 PENDING 상태를 유지하고 예외가 전파되지 않는다.")
        @Test
        void staysPending_whenKafkaSendFails() {
            // arrange
            OutboxEventModel event = createPendingEvent();
            given(outboxEventRepository.findPending(anyInt())).willReturn(List.of(event));
            CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("Kafka 연결 실패"));
            given(kafkaTemplate.send("catalog-events", "10", event.getPayload())).willReturn(failed);

            // act & assert
            assertDoesNotThrow(() -> scheduler.publishPendingEvents());
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        }
    }
}
