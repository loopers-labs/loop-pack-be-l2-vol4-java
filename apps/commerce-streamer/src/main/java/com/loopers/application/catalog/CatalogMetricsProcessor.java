package com.loopers.application.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.catalog.EventHandledEntity;
import com.loopers.infrastructure.catalog.EventHandledJpaRepository;
import com.loopers.infrastructure.catalog.ProductDailyMetricsJpaRepository;
import com.loopers.infrastructure.catalog.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Map;

/**
 * CDC(변경 데이터 캡처)로 흘러온 카탈로그 이벤트를 product_metrics(평생 누적)와
 * product_daily_metrics(상품×일자별, 주간/월간 랭킹 배치 입력)에 함께 반영하는 프로젝터.
 * REQUIRES_NEW로 별도 트랜잭션을 사용해, 호출부(Consumer)의 트랜잭션 상태와 무관하게
 * 이벤트 하나 처리 실패가 다른 이벤트 처리에 영향을 주지 않도록 격리한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogMetricsProcessor {

    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final ProductDailyMetricsJpaRepository productDailyMetricsJpaRepository;
    private final ObjectMapper objectMapper;

    /**
     * 이벤트를 product_metrics에 반영한다. 중복 eventId는 event_handled 테이블로 멱등 처리한다.
     * 랭킹 점수 계산은 CatalogRankingScoreMapper가 단일 담당한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String eventType, String eventId, String topic, String payload) throws Exception {
        if (eventHandledJpaRepository.existsByEventId(eventId)) {
            log.debug("[CATALOG] 중복 이벤트 skip — eventId={}, eventType={}", eventId, eventType);
            return;
        }

        Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
        switch (eventType) {
            case "OrderItemSoldEvent" -> {
                Map<String, Integer> productQtyMap = objectMapper.convertValue(
                    data.get("productQtyMap"), new TypeReference<>() {}
                );
                productQtyMap.forEach((productId, qty) -> {
                    productMetricsJpaRepository.upsertOrderCount(Long.parseLong(productId), qty);
                    productDailyMetricsJpaRepository.upsertOrderCount(Long.parseLong(productId), qty);
                });
            }
            case "ProductLikedEvent" -> {
                Long productId = ((Number) data.get("productId")).longValue();
                productMetricsJpaRepository.upsertLikeCountIncrement(productId);
                productDailyMetricsJpaRepository.upsertLikeCountIncrement(productId);
            }
            case "ProductUnlikedEvent" -> {
                Long productId = ((Number) data.get("productId")).longValue();
                productMetricsJpaRepository.upsertLikeCountDecrement(productId);
                productDailyMetricsJpaRepository.upsertLikeCountDecrement(productId);
            }
            case "ProductViewedEvent" -> {
                Long productId = ((Number) data.get("productId")).longValue();
                productMetricsJpaRepository.upsertViewCountIncrement(productId);
                productDailyMetricsJpaRepository.upsertViewCountIncrement(productId);
            }
            default -> log.warn("[CATALOG] 알 수 없는 이벤트 타입 — eventType={}", eventType);
        }

        eventHandledJpaRepository.save(new EventHandledEntity(eventId, topic));
    }
}
