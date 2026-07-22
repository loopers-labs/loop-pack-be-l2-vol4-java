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
import com.loopers.kafka.event.ProductViewEventPayload;
import com.loopers.support.monitoring.EventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductMetricsEventServiceTest {

    @DisplayName("상품 좋아요 이벤트를 처리할 때, ")
    @Nested
    class Process {

        @DisplayName("처음 받은 좋아요 이벤트이면 처리 이력을 저장하고 product_metrics 일간 좋아요 증감 수를 1 증가시킨다.")
        @Test
        void upsertsProductMetrics_whenEventIsNew() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));
            EventMessage message = fixture.productLikeMessage("event-1", "PRODUCT_LIKED", 1L, 30L, occurredAt);

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(message);

            // assert
            ProductMetrics metrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(LocalDate.of(2026, 7, 20), 1L));
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.UPDATED),
                () -> assertThat(fixture.eventHandledRepository.exists("event-1")).isTrue(),
                () -> assertThat(metrics.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 20)),
                () -> assertThat(metrics.getLikeCount()).isEqualTo(1L),
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

        @DisplayName("조회 이벤트이면 product_metrics 일간 조회 수를 1 증가시킨다.")
        @Test
        void incrementsViewCount_whenProductViewedEventIsNew() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(
                fixture.productViewedMessage("event-view", 1L, occurredAt)
            );

            // assert
            ProductMetrics metrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(LocalDate.of(2026, 7, 20), 1L));
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.UPDATED),
                () -> assertThat(metrics.getViewCount()).isEqualTo(1L),
                () -> assertThat(metrics.getLikeCount()).isZero(),
                () -> assertThat(metrics.getSalesCount()).isZero(),
                () -> assertThat(metrics.getSalesAmount()).isZero()
            );
        }

        @DisplayName("좋아요 취소 이벤트이면 product_metrics 일간 좋아요 증감 수를 1 감소시킨다.")
        @Test
        void decrementsLikeCount_whenProductUnlikedEventIsNew() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(
                fixture.productLikeMessage("event-unlike", "PRODUCT_UNLIKED", 1L, 29L, occurredAt)
            );

            // assert
            ProductMetrics metrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(LocalDate.of(2026, 7, 20), 1L));
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.UPDATED),
                () -> assertThat(metrics.getLikeCount()).isEqualTo(-1L),
                () -> assertThat(metrics.getLastLikeEventAt()).isEqualTo(occurredAt)
            );
        }

        @DisplayName("주문 완료 이벤트이면 상품별 일간 판매 수량과 판매 금액을 누적한다.")
        @Test
        void accumulatesSalesCountAndAmount_whenOrderPaidEventIsNew() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 20, 11, 0, 0, 0, ZoneId.of("Asia/Seoul"));

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(
                "order-events",
                fixture.orderPaidMessage("event-order", occurredAt)
            );

            // assert
            ProductMetrics firstMetrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(LocalDate.of(2026, 7, 20), 1L));
            ProductMetrics secondMetrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(LocalDate.of(2026, 7, 20), 2L));
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.UPDATED),
                () -> assertThat(firstMetrics.getSalesCount()).isEqualTo(3L),
                () -> assertThat(firstMetrics.getSalesAmount()).isEqualTo(3_000L),
                () -> assertThat(secondMetrics.getSalesCount()).isEqualTo(1L),
                () -> assertThat(secondMetrics.getSalesAmount()).isEqualTo(500L)
            );
        }

        @DisplayName("같은 상품의 좋아요 이벤트라도 발생일이 다르면 날짜별 product_metrics row를 분리해 저장한다.")
        @Test
        void upsertsProductMetricsByMetricDate_whenEventsOccurOnDifferentDays() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZoneId zone = ZoneId.of("Asia/Seoul");
            ZonedDateTime firstDay = ZonedDateTime.of(2026, 7, 20, 23, 59, 0, 0, zone);
            ZonedDateTime nextDay = ZonedDateTime.of(2026, 7, 21, 0, 1, 0, 0, zone);

            // act
            fixture.service.process(fixture.productLikeMessage("event-1", "PRODUCT_LIKED", 1L, 3L, firstDay));
            fixture.service.process(fixture.productLikeMessage("event-2", "PRODUCT_LIKED", 1L, 4L, nextDay));

            // assert
            ProductMetrics firstMetrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(LocalDate.of(2026, 7, 20), 1L));
            ProductMetrics nextMetrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(LocalDate.of(2026, 7, 21), 1L));
            assertAll(
                () -> assertThat(firstMetrics.getLikeCount()).isEqualTo(1L),
                () -> assertThat(nextMetrics.getLikeCount()).isEqualTo(1L),
                () -> assertThat(fixture.productMetricsRepository.metrics).hasSize(2)
            );
        }

        @DisplayName("이미 처리한 eventId이면 product_metrics를 다시 갱신하지 않는다.")
        @Test
        void skipsProductMetricsUpdate_whenEventIsDuplicate() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime occurredAt = ZonedDateTime.now();
            EventMessage message = fixture.productLikeMessage("event-1", "PRODUCT_LIKED", 1L, 3L, occurredAt);
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

        @DisplayName("고유한 오래된 좋아요 이벤트도 발생일의 일간 증감 수에 반영한다.")
        @Test
        void appliesUniqueOlderLikeEventToDailyDelta() throws Exception {
            // arrange
            TestFixture fixture = new TestFixture();
            ZonedDateTime latestAt = ZonedDateTime.now();
            fixture.service.process(fixture.productLikeMessage("event-1", "PRODUCT_LIKED", 1L, 5L, latestAt));

            // act
            ProductMetricsEventService.ProcessResult result = fixture.service.process(
                fixture.productLikeMessage("event-2", "PRODUCT_LIKED", 1L, 3L, latestAt.minusSeconds(1))
            );

            // assert
            ProductMetrics metrics = fixture.productMetricsRepository.metrics.get(new MetricsKey(latestAt.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate(), 1L));
            assertAll(
                () -> assertThat(result).isEqualTo(ProductMetricsEventService.ProcessResult.UPDATED),
                () -> assertThat(fixture.eventHandledRepository.exists("event-2")).isTrue(),
                () -> assertThat(metrics.getLikeCount()).isEqualTo(2L),
                () -> assertThat(metrics.getLastLikeEventAt()).isEqualTo(latestAt),
                () -> assertThat(fixture.productMetricsRepository.saveCount).isEqualTo(2)
            );
        }
    }

    private record MetricsKey(LocalDate metricDate, Long productId) {
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
            String eventType,
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
                eventType,
                "PRODUCT",
                String.valueOf(productId),
                objectMapper.writeValueAsString(payload),
                occurredAt
            );
        }

        private EventMessage productViewedMessage(String eventId, Long productId, ZonedDateTime occurredAt)
            throws Exception {
            ProductViewEventPayload payload = new ProductViewEventPayload(productId, "user1", occurredAt);
            return new EventMessage(
                eventId,
                "PRODUCT_VIEWED",
                "PRODUCT",
                String.valueOf(productId),
                objectMapper.writeValueAsString(payload),
                occurredAt
            );
        }

        private EventMessage orderPaidMessage(String eventId, ZonedDateTime occurredAt) throws Exception {
            Map<String, Object> payload = Map.of(
                "orderId", 1L,
                "userId", "user1",
                "originalAmount", 3_500L,
                "discountAmount", 0L,
                "finalAmount", 3_500L,
                "paidAt", occurredAt,
                "items", List.of(
                    Map.of(
                        "productId", 1L,
                        "productName", "상품1",
                        "quantity", 2,
                        "unitPrice", 1_000L,
                        "lineAmount", 2_000L
                    ),
                    Map.of(
                        "productId", 1L,
                        "productName", "상품1",
                        "quantity", 1,
                        "unitPrice", 1_000L,
                        "lineAmount", 1_000L
                    ),
                    Map.of(
                        "productId", 2L,
                        "productName", "상품2",
                        "quantity", 1,
                        "unitPrice", 500L,
                        "lineAmount", 500L
                    )
                )
            );
            return new EventMessage(
                eventId,
                "ORDER_PAID",
                "ORDER",
                "1",
                objectMapper.writeValueAsString(payload),
                occurredAt
            );
        }
    }

    private static class FakeProductMetricsRepository implements ProductMetricsRepository {
        private final Map<MetricsKey, ProductMetrics> metrics = new HashMap<>();
        private int saveCount = 0;

        @Override
        public ProductMetrics save(ProductMetrics productMetrics) {
            saveCount++;
            metrics.put(new MetricsKey(productMetrics.getMetricDate(), productMetrics.getProductId()), productMetrics);
            return productMetrics;
        }

        @Override
        public Optional<ProductMetrics> findByMetricDateAndProductId(LocalDate metricDate, Long productId) {
            return Optional.ofNullable(metrics.get(new MetricsKey(metricDate, productId)));
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
