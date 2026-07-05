package com.loopers.application.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.metrics.CatalogEventType;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CatalogEventFacade {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;

    /**
     * 한 배치를 한 트랜잭션으로 처리한다 (batch + manual ack 함정 대비 — 항목별 멱등이 필수).
     * 처음 보는 이벤트면 표시(markIfFirst)와 집계 반영을 같은 TX 로 묶어 원자성을 보장한다.
     */
    @Transactional
    public void handle(List<CatalogEventMessage> messages) {
        for (CatalogEventMessage message : messages) {
            if (eventHandledRepository.markIfFirst(message.eventId())) {
                applyMetric(message);
            }
        }
    }

    private static final String STOCK_CHANGED = "STOCK_CHANGED";
    private static final String VIEWED = "VIEWED";

    private void applyMetric(CatalogEventMessage message) {
        if (STOCK_CHANGED.equals(message.eventType())) {
            applyStockState(message);
            return;
        }
        if (VIEWED.equals(message.eventType())) {
            productMetricsRepository.applyViewDelta(message.aggregateId(), 1);
            return;
        }
        // like/unlike 처럼 '누적(delta)'되는 이벤트: 순서와 무관(commutative)해 멱등만으로 정확.
        CatalogEventType.from(message.eventType())
            .ifPresent(type -> productMetricsRepository.applyLikeDelta(message.aggregateId(), type.likeDelta()));
    }

    // 재고는 '절대 상태'라 delta 가 아니라 최신 값으로 덮어쓴다 — version 으로 최신성을 가드한다.
    private void applyStockState(CatalogEventMessage message) {
        JsonNode data = message.data();
        long quantity = data.get("quantity").asLong();
        long version = data.get("version").asLong();
        productMetricsRepository.applyStockState(message.aggregateId(), quantity, version);
    }
}
