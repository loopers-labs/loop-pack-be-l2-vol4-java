package com.loopers.application.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * catalog-events 처리기 — LikeChanged / ProductViewed 이벤트를 product_metrics 에 반영.
 * <p>
 * 개별 이벤트 처리 전 event_handled 로 멱등 검사. 처리 완료 후 event_handled 에 기록.
 * 예외가 나면 상위 리스너에 던져 Ack 을 유보시킨다 — 다음 poll 에서 재시도.
 */
@RequiredArgsConstructor
@Component
public class CatalogEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventHandler.class);
    public static final String CONSUMER_GROUP = "metrics-catalog";

    private final ObjectMapper objectMapper;
    private final ProductMetricsService productMetricsService;
    private final EventHandledService eventHandledService;

    public void handle(EventEnvelope envelope) {
        if (!envelope.hasEventId()) {
            log.warn("event-id 헤더 누락 — 스킵. eventType={}", envelope.eventType());
            return;
        }
        if (eventHandledService.isHandled(envelope.eventId(), CONSUMER_GROUP)) {
            return;
        }

        JsonNode payload = parse(envelope.payload());
        switch (envelope.eventType()) {
            case "LikeChanged" -> handleLikeChanged(payload);
            case "ProductViewed" -> handleProductViewed(payload);
            default -> log.info("catalog-events: 미지원 eventType={} — 스킵", envelope.eventType());
        }

        eventHandledService.markHandled(envelope.eventId(), CONSUMER_GROUP, envelope.eventType());
    }

    private void handleLikeChanged(JsonNode payload) {
        Long productId = payload.get("productId").asLong();
        String action = payload.get("action").asText();
        ZonedDateTime occurredAt = ZonedDateTime.parse(payload.get("occurredAt").asText());
        long delta = "LIKED".equals(action) ? 1L : -1L;
        productMetricsService.applyLikeDelta(productId, delta, occurredAt);
    }

    private void handleProductViewed(JsonNode payload) {
        Long productId = payload.get("productId").asLong();
        ZonedDateTime occurredAt = ZonedDateTime.parse(payload.get("occurredAt").asText());
        productMetricsService.applyView(productId, occurredAt);
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("이벤트 payload 파싱 실패: " + e.getMessage(), e);
        }
    }
}
