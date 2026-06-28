package com.loopers.application.catalog.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.catalog.metrics.ProductMetrics;
import com.loopers.domain.catalog.metrics.ProductMetricsRepository;
import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import com.loopers.kafka.event.EventMessage;
import com.loopers.kafka.event.ProductLikeEventPayload;
import com.loopers.support.monitoring.EventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductMetricsEventServiceTest {

    @DisplayName("상품 좋아요 이벤트를 처리할 때, ")
    @Nested
    class Process {

        @DisplayName("처음 받은 이벤트이면 처리 이력을 저장하고 product_metrics 좋아요 수를 갱신한다.")
        @Test
        void upsertsProductMetrics_whenEventIsNew() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime occurredAt = ZonedDateTime.now();
            EventMessage message = fixture.productLikeMessage("event-1", 1L, 3L, occurredAt);

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(message);

            // assert
            ProductMetrics metrics = fixture.productMetricsRepository.metrics.get(1L);
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.UPDATED),
                () -> assertThat(fixture.eventHandledRepository.exists("event-1")).isTrue(),
                () -> assertThat(metrics.getLikeCount()).isEqualTo(3L),
                () -> assertThat(metrics.getLastLikeEventAt()).isEqualTo(occurredAt),
                () -> assertThat(fixture.meterRegistry.counter(
                    "loopers.kafka.consumer.success.count",
                    "topic", "catalog-events",
                    "eventType", "PRODUCT_LIKED",
                    "result", "success"
                ).count()).isEqualTo(1.0),
                () -> assertThat(fixture.meterRegistry.counter(
                    "loopers.product_metrics.update.count",
                    "topic", "catalog-events",
                    "eventType", "PRODUCT_LIKED",
                    "result", "success"
                ).count()).isEqualTo(1.0)
            );
        }

        @DisplayName("이미 처리한 eventId이면 product_metrics를 다시 갱신하지 않는다.")
        @Test
        void skipsProductMetricsUpdate_whenEventIsDuplicate() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime occurredAt = ZonedDateTime.now();
            EventMessage message = fixture.productLikeMessage("event-1", 1L, 3L, occurredAt);
            fixture.service.process(message);

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(message);

            // assert
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.DUPLICATE),
                () -> assertThat(fixture.productMetricsRepository.saveCount).isEqualTo(1),
                () -> assertThat(fixture.meterRegistry.counter(
                    "loopers.kafka.consumer.duplicate.count",
                    "topic", "catalog-events",
                    "eventType", "PRODUCT_LIKED",
                    "result", "duplicate"
                ).count()).isEqualTo(1.0)
            );
        }

        @DisplayName("이미 더 최신 이벤트가 반영되어 있으면 오래된 이벤트는 저장하되 집계를 덮어쓰지 않는다.")
        @Test
        void doesNotOverwriteMetrics_whenEventIsStale() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime latestAt = ZonedDateTime.now();
            fixture.service.process(fixture.productLikeMessage("event-1", 1L, 5L, latestAt));

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(
                fixture.productLikeMessage("event-2", 1L, 3L, latestAt.minusSeconds(1))
            );

            // assert
            ProductMetrics metrics = fixture.productMetricsRepository.metrics.get(1L);
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.STALE),
                () -> assertThat(fixture.eventHandledRepository.exists("event-2")).isTrue(),
                () -> assertThat(metrics.getLikeCount()).isEqualTo(5L),
                () -> assertThat(metrics.getLastLikeEventAt()).isEqualTo(latestAt),
                () -> assertThat(fixture.productMetricsRepository.saveCount).isEqualTo(1)
            );
        }
    }

    private static class TestFixture {
        private final ObjectMapper objectMapper = objectMapper();
        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final FakeProductMetricsRepository productMetricsRepository = new FakeProductMetricsRepository();
        private final FakeEventHandledRepository eventHandledRepository = new FakeEventHandledRepository();
        private final ProductMetricsEventService service = new ProductMetricsEventService(
            productMetricsRepository,
            eventHandledRepository,
            objectMapper,
            new EventMetrics(meterRegistry)
        );

        private EventMessage productLikeMessage(
            String eventId,
            Long productId,
            Long likeCount,
            ZonedDateTime occurredAt
        ) throws Exception {
            ProductLikeEventPayload payload = new ProductLikeEventPayload(
                productId,
                "user1",
                true,
                likeCount,
                occurredAt
            );
            return new EventMessage(
                eventId,
                "PRODUCT_LIKED",
                "PRODUCT",
                String.valueOf(productId),
                objectMapper.writeValueAsString(payload),
                occurredAt
            );
        }
    }

    private static class FakeProductMetricsRepository implements ProductMetricsRepository {
        private final Map<Long, ProductMetrics> metrics = new HashMap<>();
        private int saveCount = 0;

        @Override
        public ProductMetrics save(ProductMetrics productMetrics) {
            saveCount++;
            metrics.put(productMetrics.getProductId(), productMetrics);
            return productMetrics;
        }

        @Override
        public Optional<ProductMetrics> findByProductId(Long productId) {
            return Optional.ofNullable(metrics.get(productId));
        }
    }

    private static class FakeEventHandledRepository implements EventHandledRepository {
        private final Map<String, EventHandled> handled = new HashMap<>();

        @Override
        public boolean exists(String eventId) {
            return handled.containsKey(eventId);
        }

        @Override
        public EventHandled save(EventHandled eventHandled) {
            handled.put(eventHandled.getEventId(), eventHandled);
            return eventHandled;
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
