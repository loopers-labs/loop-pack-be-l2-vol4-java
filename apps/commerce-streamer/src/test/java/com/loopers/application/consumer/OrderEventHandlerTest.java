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

class OrderEventHandlerTest {

    private ProductMetricsService metricsService;
    private OrderEventHandler handler;

    @BeforeEach
    void setUp() {
        metricsService = new ProductMetricsService(new FakeProductMetricsRepository());
        EventHandledService handledService = new EventHandledService(new FakeEventHandledRepository());
        handler = new OrderEventHandler(new ObjectMapper(), metricsService, handledService);
    }

    private EventEnvelope envelope(String eventType, String payload) {
        return new EventEnvelope(UUID.randomUUID().toString(), eventType, "Order", "1", payload);
    }

    @DisplayName("OrderCompleted 는 각 라인의 상품 판매량을 quantity 만큼 증가시킨다.")
    @Test
    void orderCompletedIncrementsSales() {
        String payload = """
            {"orderId":42,"userId":100,"finalPrice":5000,
             "lines":[{"productId":1,"quantity":2,"unitPrice":1000},
                      {"productId":2,"quantity":1,"unitPrice":3000}],
             "occurredAt":"2026-07-03T10:00:00+09:00"}
            """;

        handler.handle(envelope("OrderCompleted", payload));

        assertThat(metricsService.get(1L).getSalesCount()).isEqualTo(2);
        assertThat(metricsService.get(2L).getSalesCount()).isEqualTo(1);
    }
}
