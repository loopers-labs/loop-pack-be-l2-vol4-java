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
 * order-events 처리기 — OrderCompleted 로 판매량 집계. PaymentSettled 는 현 단계에서 별도 집계 없이 로깅만.
 */
@RequiredArgsConstructor
@Component
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    public static final String CONSUMER_GROUP = "metrics-order";

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
            case "OrderCompleted" -> handleOrderCompleted(payload);
            case "PaymentSettled" -> log.debug("PaymentSettled 수신 — 별도 집계 없음. paymentId={}",
                payload.path("paymentId").asLong());
            default -> log.info("order-events: 미지원 eventType={} — 스킵", envelope.eventType());
        }

        eventHandledService.markHandled(envelope.eventId(), CONSUMER_GROUP, envelope.eventType());
    }

    private void handleOrderCompleted(JsonNode payload) {
        ZonedDateTime occurredAt = ZonedDateTime.parse(payload.get("occurredAt").asText());
        JsonNode lines = payload.get("lines");
        if (lines == null || !lines.isArray()) {
            return;
        }
        for (JsonNode line : lines) {
            Long productId = line.get("productId").asLong();
            int quantity = line.get("quantity").asInt();
            productMetricsService.applySales(productId, quantity, occurredAt);
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("이벤트 payload 파싱 실패: " + e.getMessage(), e);
        }
    }
}
