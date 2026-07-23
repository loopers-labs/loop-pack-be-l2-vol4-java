package com.loopers.application.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.application.ranking.RankingScoreUpdater;
import com.loopers.domain.productmetrics.ProductMetricsModel;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * catalog-events 처리 — product_metrics 집계 반영 + event_handled 멱등 기록을 한 트랜잭션으로 묶는다.
 * 메시지 페이로드 파싱까지 이 계층에서 책임져, interfaces(Kafka 어댑터) 계층은 Kafka 메커니즘(ack 등)만 다루도록 한다.
 *
 * version/updated_at 기준 최신 이벤트만 반영하는 원칙은 "덮어쓰기(override)"형 데이터에 적용되는 것으로,
 * 좋아요 수처럼 증분(increment/decrement)되는 카운터에는 적용하지 않는다 — 각 이벤트는 도착 순서와 무관하게
 * 정확히 한 번만 반영되면 되므로, event_handled 기반 멱등 처리만으로 정합성이 보장된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventFacade {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final RankingScoreUpdater rankingScoreUpdater;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String rawPayload) {
        CatalogEventPayload payload = parse(rawPayload);

        if (eventHandledRepository.existsByEventId(payload.eventId())) {
            log.info("이미 처리된 이벤트 — eventId={}", payload.eventId());
            return;
        }

        ProductMetricsModel metrics = productMetricsRepository.findByProductId(payload.productId())
            .orElseGet(() -> productMetricsRepository.save(new ProductMetricsModel(payload.productId())));

        switch (payload.eventType()) {
            case CatalogEventPayload.PRODUCT_LIKED -> {
                metrics.incrementLikeCount();
                rankingScoreUpdater.onProductLiked(payload.productId());
            }
            case CatalogEventPayload.PRODUCT_UNLIKED -> {
                metrics.decrementLikeCount();
                rankingScoreUpdater.onProductUnliked(payload.productId());
            }
            case CatalogEventPayload.PRODUCT_VIEWED -> {
                metrics.incrementViewCount();
                rankingScoreUpdater.onProductViewed(payload.productId());
            }
            default -> throw new IllegalArgumentException("지원하지 않는 catalog 이벤트 타입입니다: " + payload.eventType());
        }

        eventHandledRepository.save(new EventHandledModel(payload.eventId()));
    }

    private CatalogEventPayload parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, CatalogEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("catalog 이벤트 페이로드 파싱에 실패했습니다: " + rawPayload, e);
        }
    }
}
