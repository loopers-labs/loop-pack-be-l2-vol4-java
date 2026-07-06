package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 1건을 멱등하게 집계에 반영한다.
 * 멱등 장부(event_handled) 기록과 집계 upsert 를 한 트랜잭션으로 묶는다 —
 * "장부엔 적었는데 집계 전에 죽음"(누락) / "집계했는데 장부 기록 전에 죽음"(중복) 의 틈을 없애기 위해.
 */
@RequiredArgsConstructor
@Component
public class MetricsAggregationService {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public void aggregate(String eventId, String eventType, JsonNode payload) {
        if (eventHandledRepository.alreadyHandled(eventId)) {
            return; // 중복 배달(At Least Once 의 대가) — 이미 반영된 이벤트는 통째로 skip
        }
        eventHandledRepository.markHandled(eventId);

        switch (eventType) {
            case "ProductLikedEvent" -> productMetricsRepository.increaseLikeCount(payload.get("productId").asLong());
            case "ProductUnlikedEvent" -> productMetricsRepository.decreaseLikeCount(payload.get("productId").asLong());
            case "ProductViewedEvent" -> productMetricsRepository.increaseViewCount(payload.get("productId").asLong());
            // 판매량은 주문 생성 기준으로 집계한다 (결제 실패 시 과대 집계 가능 — 파생 지표라 감수, 정밀화는 추후 과제).
            case "OrderCreatedEvent" -> payload.get("items").forEach(item ->
                productMetricsRepository.increaseSaleCount(item.get("productId").asLong(), item.get("quantity").asInt()));
            default -> {
                // 집계 대상이 아닌 이벤트(결제 확정 등)도 장부에는 남긴다 — 재배달 시 다시 파싱하지 않도록.
            }
        }
    }
}
