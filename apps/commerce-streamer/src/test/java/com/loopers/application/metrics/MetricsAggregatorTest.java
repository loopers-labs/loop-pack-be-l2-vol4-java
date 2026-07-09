package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 집계 로직 단위 테스트 — Kafka/DB 없이 검증한다.
 * eventType 별 델타 해석 + 배치 coalescing + event_handled 멱등 필터 + fresh 마킹이 핵심.
 */
class MetricsAggregatorTest {

    private static final JsonNodeFactory J = JsonNodeFactory.instance;

    private EventEnvelope like(long eventId, long productId, long delta) {
        ObjectNode p = J.objectNode();
        p.put("productId", productId);
        p.put("delta", delta);
        return new EventEnvelope(eventId, "LIKE_CHANGED", "product", productId, 0L, "2026-07-01T00:00:00Z", p);
    }

    private EventEnvelope viewed(long eventId, long productId) {
        ObjectNode p = J.objectNode();
        p.put("productId", productId);
        return new EventEnvelope(eventId, "PRODUCT_VIEWED", "product", productId, 0L, "2026-07-01T00:00:00Z", p);
    }

    private EventEnvelope orderPaid(long eventId, long orderId, long productId, int quantity) {
        ObjectNode p = J.objectNode();
        p.put("orderId", orderId);
        ArrayNode items = p.putArray("items");
        ObjectNode item = items.addObject();
        item.put("productId", productId);
        item.put("quantity", quantity);
        return new EventEnvelope(eventId, "ORDER_PAID", "order", orderId, 0L, "2026-07-01T00:00:00Z", p);
    }

    @DisplayName("eventType별로 like/view/sales 델타를 상품별로 합산해 updater에 넘기고, fresh eventId를 마킹한다.")
    @Test
    void aggregatesByType() {
        EventHandledRepository handled = mock(EventHandledRepository.class);
        ProductMetricsUpdater updater = mock(ProductMetricsUpdater.class);
        when(handled.findHandled(any(), any())).thenReturn(Set.of());
        MetricsAggregator aggregator = new MetricsAggregator(handled, updater);

        // 상품 10: 좋아요 +1 +1 +1 -1 = +2 / 상품 20: 좋아요 +1
        // 상품 10: 조회 +1 / 상품 30: 판매 3
        List<EventEnvelope> batch = List.of(
                like(1, 10, +1), like(2, 10, +1), like(3, 20, +1), like(4, 10, +1), like(5, 10, -1),
                viewed(6, 10),
                orderPaid(7, 100, 30, 3)
        );

        aggregator.apply(batch);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> likeCaptor = ArgumentCaptor.forClass(Map.class);
        verify(updater).applyLikeDeltas(likeCaptor.capture());
        assertThat(likeCaptor.getValue()).containsEntry(10L, 2L).containsEntry(20L, 1L).hasSize(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> viewCaptor = ArgumentCaptor.forClass(Map.class);
        verify(updater).applyViewDeltas(viewCaptor.capture());
        assertThat(viewCaptor.getValue()).containsEntry(10L, 1L).hasSize(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> salesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(updater).applySalesDeltas(salesCaptor.capture());
        assertThat(salesCaptor.getValue()).containsEntry(30L, 3L).hasSize(1);

        verify(handled).markHandled(eq(MetricsAggregator.CONSUMER_GROUP), any());
    }

    @DisplayName("이미 처리된 eventId는 건너뛴다(멱등) — fresh만 집계·마킹.")
    @Test
    void skipsAlreadyHandled() {
        EventHandledRepository handled = mock(EventHandledRepository.class);
        ProductMetricsUpdater updater = mock(ProductMetricsUpdater.class);
        // eventId 1,2는 이미 처리됨 → 3만 fresh
        when(handled.findHandled(any(), any())).thenReturn(Set.of(1L, 2L));
        MetricsAggregator aggregator = new MetricsAggregator(handled, updater);

        aggregator.apply(List.of(like(1, 10, +1), like(2, 10, +1), like(3, 10, +1)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> likeCaptor = ArgumentCaptor.forClass(Map.class);
        verify(updater).applyLikeDeltas(likeCaptor.capture());
        assertThat(likeCaptor.getValue()).containsEntry(10L, 1L); // 3번만 반영
        verify(handled).markHandled(MetricsAggregator.CONSUMER_GROUP, List.of(3L));
    }

    @DisplayName("배치 전체가 이미 처리됐으면 아무것도 반영하지 않는다.")
    @Test
    void noOpWhenAllHandled() {
        EventHandledRepository handled = mock(EventHandledRepository.class);
        ProductMetricsUpdater updater = mock(ProductMetricsUpdater.class);
        when(handled.findHandled(any(), any())).thenReturn(Set.of(1L, 2L));
        MetricsAggregator aggregator = new MetricsAggregator(handled, updater);

        aggregator.apply(List.of(like(1, 10, +1), like(2, 10, +1)));

        verify(updater, never()).applyLikeDeltas(any());
        verify(handled, never()).markHandled(any(), any());
    }
}
