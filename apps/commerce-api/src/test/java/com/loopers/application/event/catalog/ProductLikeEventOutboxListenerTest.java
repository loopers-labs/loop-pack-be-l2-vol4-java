package com.loopers.application.event.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.catalog.like.ProductLikeChangedApplicationEvent;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.kafka.event.ProductLikeEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductLikeEventOutboxListenerTest {

    @DisplayName("상품 좋아요 ApplicationEvent를 catalog-events Outbox로 저장한다.")
    @Test
    void savesCatalogOutbox_whenProductLikeEventIsHandled() throws Exception {
        // arrange
        ObjectMapper objectMapper = objectMapper();
        FakeEventOutboxRepository repository = new FakeEventOutboxRepository();
        ProductLikeEventOutboxListener listener = new ProductLikeEventOutboxListener(repository, objectMapper);
        ZonedDateTime occurredAt = ZonedDateTime.now();

        // act
        listener.handle(ProductLikeChangedApplicationEvent.liked(1L, "user1", 3L, occurredAt));

        // assert
        EventOutbox outbox = repository.outboxes.get(0);
        ProductLikeEventPayload payload = objectMapper.readValue(outbox.getPayload(), ProductLikeEventPayload.class);
        assertAll(
            () -> assertThat(outbox.getTopic()).isEqualTo(EventOutbox.TOPIC_CATALOG_EVENTS),
            () -> assertThat(outbox.getPartitionKey()).isEqualTo("1"),
            () -> assertThat(outbox.getEventType()).isEqualTo(EventOutbox.EVENT_PRODUCT_LIKED),
            () -> assertThat(outbox.getAggregateType()).isEqualTo(EventOutbox.AGGREGATE_PRODUCT),
            () -> assertThat(outbox.getAggregateId()).isEqualTo("1"),
            () -> assertThat(payload.productId()).isEqualTo(1L),
            () -> assertThat(payload.userId()).isEqualTo("user1"),
            () -> assertThat(payload.liked()).isTrue(),
            () -> assertThat(payload.likeCount()).isEqualTo(3L),
            () -> assertThat(payload.occurredAt()).isEqualTo(occurredAt)
        );
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

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
