package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsUpdater;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * catalog-events / order-events 배치를 받아 product_metrics 측정값에 반영하는 집계 로직.
 *
 * <p><b>멱등 + coalescing</b>: 배치 내 같은 productId 델타를 메모리에서 합산해 상품당 UPDATE 1회로 줄이고
 * (hot row 완화, 가산=교환법칙이라 순서 무관), event_handled 로 이미 처리한 eventId 는 건너뛴다.
 * 집계 반영과 처리 마킹을 <b>한 트랜잭션</b>으로 묶어 재전달 시 이중 반영을 막는다(컨슈머가 커밋 후 ack).
 *
 * <p>eventType 별 해석:
 * <ul>
 *   <li>{@code LIKE_CHANGED}  payload {productId, delta} → like_count += delta</li>
 *   <li>{@code PRODUCT_VIEWED} payload {productId}        → view_count += 1</li>
 *   <li>{@code ORDER_PAID}    payload {items:[{productId, quantity}]} → sales_count += quantity</li>
 * </ul>
 * 알 수 없는 eventType 은 로그만 남기고 스킵하되 handled 로 마킹한다(무한 재처리 방지 — DLT-lite).
 */
@Component
@RequiredArgsConstructor
public class MetricsAggregator {

    public static final String CONSUMER_GROUP = "metrics-aggregator";

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregator.class);

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsUpdater productMetricsUpdater;

    @Transactional
    public void apply(List<EventEnvelope> envelopes) {
        // 배치 내 중복 eventId 제거(먼저 온 것 유지) — 멱등 1차 방어
        Map<Long, EventEnvelope> byEventId = new LinkedHashMap<>();
        for (EventEnvelope e : envelopes) {
            if (e.eventId() != null) {
                byEventId.putIfAbsent(e.eventId(), e);
            }
        }
        if (byEventId.isEmpty()) {
            return;
        }

        // 이미 처리한 eventId 제거 — 멱등 2차 방어(재전달 대비)
        Set<Long> handled = eventHandledRepository.findHandled(CONSUMER_GROUP, byEventId.keySet());
        List<Long> freshIds = byEventId.keySet().stream()
                .filter(id -> !handled.contains(id))
                .toList();
        if (freshIds.isEmpty()) {
            return;
        }

        Map<Long, Long> likeDeltas = new HashMap<>();
        Map<Long, Long> viewDeltas = new HashMap<>();
        Map<Long, Long> salesDeltas = new HashMap<>();

        for (Long id : freshIds) {
            EventEnvelope e = byEventId.get(id);
            JsonNode payload = e.payload();
            switch (e.eventType()) {
                case "LIKE_CHANGED" -> likeDeltas.merge(
                        payload.get("productId").asLong(), payload.get("delta").asLong(), Long::sum);
                case "PRODUCT_VIEWED" -> viewDeltas.merge(
                        payload.get("productId").asLong(), 1L, Long::sum);
                case "ORDER_PAID" -> {
                    for (JsonNode item : payload.get("items")) {
                        salesDeltas.merge(item.get("productId").asLong(), item.get("quantity").asLong(), Long::sum);
                    }
                }
                default -> log.warn("알 수 없는 eventType 스킵(handled 마킹): eventId={}, type={}", id, e.eventType());
            }
        }

        productMetricsUpdater.applyLikeDeltas(likeDeltas);
        productMetricsUpdater.applyViewDeltas(viewDeltas);
        productMetricsUpdater.applySalesDeltas(salesDeltas);
        eventHandledRepository.markHandled(CONSUMER_GROUP, freshIds);

        log.debug("product_metrics 집계: fresh={}, like={}, view={}, sales={}",
                freshIds.size(), likeDeltas.size(), viewDeltas.size(), salesDeltas.size());
    }
}
