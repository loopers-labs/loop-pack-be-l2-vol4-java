package com.loopers.application.catalog.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.catalog.ranking.RankingRepository;
import com.loopers.domain.event.handled.EventHandled;
import com.loopers.domain.event.handled.EventHandledRepository;
import com.loopers.kafka.event.EventMessage;
import com.loopers.kafka.event.ProductLikeEventPayload;
import com.loopers.kafka.event.ProductViewEventPayload;
import com.loopers.support.monitoring.EventMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductRankingEventServiceTest {

    @DisplayName("상품 조회, 좋아요, 좋아요 취소 이벤트 점수를 일간 랭킹에 반영한다.")
    @Test
    void appliesCatalogEventScoresToDailyRanking() throws Exception {
        // arrange
        TestFixture fixture = new TestFixture();
        ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 12, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        // act
        ProductRankingEventService.ProcessResult viewed = fixture.service.process(
            "catalog-events",
            fixture.productViewedMessage("event-view", 1L, occurredAt)
        );
        ProductRankingEventService.ProcessResult liked = fixture.service.process(
            "catalog-events",
            fixture.productLikeMessage("event-like", "PRODUCT_LIKED", 1L, occurredAt.plusSeconds(1))
        );
        ProductRankingEventService.ProcessResult unliked = fixture.service.process(
            "catalog-events",
            fixture.productLikeMessage("event-unlike", "PRODUCT_UNLIKED", 1L, occurredAt.plusSeconds(2))
        );

        // assert
        LocalDate rankingDate = LocalDate.of(2026, 7, 12);
        assertAll(
            () -> assertThat(viewed).isEqualTo(ProductRankingEventService.ProcessResult.UPDATED),
            () -> assertThat(liked).isEqualTo(ProductRankingEventService.ProcessResult.UPDATED),
            () -> assertThat(unliked).isEqualTo(ProductRankingEventService.ProcessResult.UPDATED),
            () -> assertThat(fixture.rankingRepository.score(rankingDate, 1L)).isCloseTo(0.1, offset(0.0001)),
            () -> assertThat(fixture.eventHandledRepository.exists("ranking:event-view")).isTrue(),
            () -> assertThat(fixture.eventHandledRepository.exists("ranking:event-like")).isTrue(),
            () -> assertThat(fixture.eventHandledRepository.exists("ranking:event-unlike")).isTrue()
        );
    }

    @DisplayName("주문 완료 이벤트는 상품별 주문 금액에 주문 weight를 곱해 랭킹에 반영한다.")
    @Test
    void appliesOrderPaidLineAmountScoreToDailyRanking() throws Exception {
        // arrange
        TestFixture fixture = new TestFixture();
        ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 12, 11, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        // act
        ProductRankingEventService.ProcessResult result = fixture.service.process(
            "order-events",
            fixture.orderPaidMessage("event-order", occurredAt)
        );

        // assert
        LocalDate rankingDate = LocalDate.of(2026, 7, 12);
        assertAll(
            () -> assertThat(result).isEqualTo(ProductRankingEventService.ProcessResult.UPDATED),
            () -> assertThat(fixture.rankingRepository.score(rankingDate, 1L)).isEqualTo(1_200.0),
            () -> assertThat(fixture.rankingRepository.score(rankingDate, 2L)).isEqualTo(300.0)
        );
    }

    @DisplayName("이미 처리한 ranking eventId이면 Redis 점수를 다시 누적하지 않는다.")
    @Test
    void skipsRankingUpdate_whenEventIsDuplicate() throws Exception {
        // arrange
        TestFixture fixture = new TestFixture();
        ZonedDateTime occurredAt = ZonedDateTime.of(2026, 7, 12, 10, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        EventMessage message = fixture.productViewedMessage("event-view", 1L, occurredAt);
        fixture.service.process("catalog-events", message);

        // act
        ProductRankingEventService.ProcessResult result = fixture.service.process("catalog-events", message);

        // assert
        assertAll(
            () -> assertThat(result).isEqualTo(ProductRankingEventService.ProcessResult.DUPLICATE),
            () -> assertThat(fixture.rankingRepository.score(LocalDate.of(2026, 7, 12), 1L))
                .isCloseTo(0.1, offset(0.0001))
        );
    }

    private static class TestFixture {
        private final ObjectMapper objectMapper = objectMapper();
        private final FakeRankingRepository rankingRepository = new FakeRankingRepository();
        private final FakeEventHandledRepository eventHandledRepository = new FakeEventHandledRepository();
        private final ProductRankingEventService service = new ProductRankingEventService(
            rankingRepository,
            eventHandledRepository,
            objectMapper,
            new EventMetrics(new SimpleMeterRegistry())
        );

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

        private EventMessage productLikeMessage(
            String eventId,
            String eventType,
            Long productId,
            ZonedDateTime occurredAt
        ) throws Exception {
            ProductLikeEventPayload payload = new ProductLikeEventPayload(productId, "user1", true, 1L, occurredAt);
            return new EventMessage(
                eventId,
                eventType,
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
                "originalAmount", 2_500L,
                "discountAmount", 0L,
                "finalAmount", 2_500L,
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

    private record RankingKey(LocalDate date, Long productId) {
    }

    private static class FakeRankingRepository implements RankingRepository {
        private final Map<RankingKey, Double> scores = new HashMap<>();
        private final Map<String, Boolean> handled = new HashMap<>();

        @Override
        public boolean incrementScoresOnce(LocalDate date, String eventId, List<Score> requestedScores, Duration ttl) {
            if (handled.putIfAbsent(eventId, true) != null) {
                return false;
            }
            for (Score score : requestedScores) {
                RankingKey key = new RankingKey(date, score.productId());
                scores.put(key, scores.getOrDefault(key, 0.0) + score.score());
            }
            return true;
        }

        private double score(LocalDate date, Long productId) {
            return scores.getOrDefault(new RankingKey(date, productId), 0.0);
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
