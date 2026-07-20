package com.loopers.application.event.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.kafka.event.ProductViewEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductViewEventPublisherTest {

    @DisplayName("상품 상세 조회 성공 이벤트를 catalog-events Outbox로 저장한다.")
    @Test
    void savesCatalogOutbox_whenProductViewIsRecorded() throws Exception {
        // arrange
        ObjectMapper objectMapper = objectMapper();
        FakeEventOutboxRepository repository = new FakeEventOutboxRepository();
        ProductViewEventPublisher publisher = new ProductViewEventPublisher(repository, objectMapper);
        ZonedDateTime occurredAt = ZonedDateTime.now();

        // act
        publisher.publish(1L, "user1", occurredAt);

        // assert
        EventOutbox outbox = repository.outboxes.get(0);
        ProductViewEventPayload payload = objectMapper.readValue(outbox.getPayload(), ProductViewEventPayload.class);
        assertAll(
            () -> assertThat(outbox.getTopic()).isEqualTo(EventOutbox.TOPIC_CATALOG_EVENTS),
            () -> assertThat(outbox.getPartitionKey()).isEqualTo("1"),
            () -> assertThat(outbox.getEventType()).isEqualTo(EventOutbox.EVENT_PRODUCT_VIEWED),
            () -> assertThat(outbox.getAggregateType()).isEqualTo(EventOutbox.AGGREGATE_PRODUCT),
            () -> assertThat(outbox.getAggregateId()).isEqualTo("1"),
            () -> assertThat(payload.productId()).isEqualTo(1L),
            () -> assertThat(payload.userId()).isEqualTo("user1"),
            () -> assertThat(payload.occurredAt()).isEqualTo(occurredAt)
        );
    }

    @DisplayName("상품 조회 이벤트 저장 실패는 호출자에게 전파하지 않는다.")
    @Test
    void doesNotPropagateException_whenPublishSafelyFails() {
        // arrange
        ProductViewEventPublisher publisher = new ProductViewEventPublisher(
            new FailingEventOutboxRepository(),
            objectMapper()
        );

        // act & assert
        assertThatCode(() -> publisher.publishSafely(1L, "user1"))
            .doesNotThrowAnyException();
    }

    private static class FakeEventOutboxRepository implements EventOutboxRepository {
        private final List<EventOutbox> outboxes = new ArrayList<>();

        @Override
        public EventOutbox save(EventOutbox outbox) {
            outboxes.add(outbox);
            return outbox;
        }

        @Override
        public List<EventOutbox> findPendingEvents(int limit) {
            return outboxes.stream()
                .filter(EventOutbox::isPending)
                .limit(limit)
                .toList();
        }
    }

    private static class FailingEventOutboxRepository implements EventOutboxRepository {
        @Override
        public EventOutbox save(EventOutbox outbox) {
            throw new IllegalStateException("outbox save failed");
        }

        @Override
        public List<EventOutbox> findPendingEvents(int limit) {
            return List.of();
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
