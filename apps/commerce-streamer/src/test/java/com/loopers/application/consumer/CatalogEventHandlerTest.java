package com.loopers.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.domain.eventhandled.FakeEventHandledRepository;
import com.loopers.domain.metrics.FakeProductMetricsRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogEventHandlerTest {

    private FakeProductMetricsRepository metricsRepo;
    private FakeEventHandledRepository handledRepo;
    private ProductMetricsService metricsService;
    private EventHandledService handledService;
    private CatalogEventHandler handler;

    @BeforeEach
    void setUp() {
        metricsRepo = new FakeProductMetricsRepository();
        handledRepo = new FakeEventHandledRepository();
        metricsService = new ProductMetricsService(metricsRepo);
        handledService = new EventHandledService(handledRepo);
        handler = new CatalogEventHandler(new ObjectMapper(), metricsService, handledService);
    }

    private EventEnvelope envelope(String eventType, String payload) {
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, "Product", "1", payload);
    }

    @DisplayName("LikeChanged(LIKED) 는 like_count 를 1 증가시킨다.")
    @Test
    void likeIncrementsCount() {
        String payload = "{\"userId\":100,\"productId\":1,\"action\":\"LIKED\",\"occurredAt\":\"2026-07-03T10:00:00+09:00\"}";

        handler.handle(envelope("LikeChanged", payload));

        assertThat(metricsService.get(1L).getLikeCount()).isEqualTo(1);
    }

    @DisplayName("LikeChanged(UNLIKED) 는 delta -1 을 반영한다 (0 미만 방지).")
    @Test
    void unlikeDecrementsCount() {
        String liked = "{\"userId\":100,\"productId\":1,\"action\":\"LIKED\",\"occurredAt\":\"2026-07-03T10:00:00+09:00\"}";
        String unliked = "{\"userId\":100,\"productId\":1,\"action\":\"UNLIKED\",\"occurredAt\":\"2026-07-03T10:00:01+09:00\"}";
        handler.handle(envelope("LikeChanged", liked));

        handler.handle(envelope("LikeChanged", unliked));

        assertThat(metricsService.get(1L).getLikeCount()).isZero();
    }

    @DisplayName("같은 eventId 로 재수신되면 두 번째 이벤트는 무시된다 (멱등).")
    @Test
    void duplicateEventIdIsIdempotent() {
        String payload = "{\"userId\":100,\"productId\":1,\"action\":\"LIKED\",\"occurredAt\":\"2026-07-03T10:00:00+09:00\"}";
        EventEnvelope env = envelope("LikeChanged", payload);

        handler.handle(env);
        handler.handle(env);

        assertThat(metricsService.get(1L).getLikeCount()).isEqualTo(1);
    }

    @DisplayName("ProductViewed 는 view_count 를 1 증가시킨다.")
    @Test
    void viewIncrementsCount() {
        String payload = "{\"userId\":100,\"productId\":1,\"occurredAt\":\"2026-07-03T10:00:00+09:00\"}";

        handler.handle(envelope("ProductViewed", payload));

        assertThat(metricsService.get(1L).getViewCount()).isEqualTo(1);
    }
}
