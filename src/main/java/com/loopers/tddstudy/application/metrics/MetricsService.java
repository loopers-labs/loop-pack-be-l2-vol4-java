package com.loopers.tddstudy.application.metrics;

import com.loopers.tddstudy.infrastructure.metrics.*;
import com.loopers.tddstudy.messaging.CatalogEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.loopers.tddstudy.application.ranking.RankingScoreEvent;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class MetricsService {

    private final ProductMetricsJpaRepository metricsRepository;
    private final EventHandledJpaRepository eventHandledRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MetricsService(ProductMetricsJpaRepository metricsRepository,
                          EventHandledJpaRepository eventHandledRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.metricsRepository = metricsRepository;
        this.eventHandledRepository = eventHandledRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void apply(CatalogEvent event) {
        // 멱등: 이미 처리한 event_id면 skip
        if (eventHandledRepository.existsById(event.eventId())) {
            return;
        }
        ProductMetrics metrics = metricsRepository.findById(event.productId())
                .orElseGet(() -> new ProductMetrics(event.productId()));

        // version 가드: 이미 더 최신 이벤트를 반영했다면 오래된 이벤트는 무시
        // (순서는 파티션키로 보장 → 재전송/경합에 대한 2차 방어, delta 유실 없음)
        if (event.occurredAt() < metrics.getLastEventAt()) {
            eventHandledRepository.save(new EventHandled(event.eventId()));  // 재처리 방지 기록
            return;
        }

        switch (event.eventType()) {
            case "PRODUCT_LIKED"   -> metrics.addLike(event.amount());
            case "PRODUCT_UNLIKED" -> metrics.addLike(-event.amount());
            case "ORDER_SALES"     -> metrics.addSales(event.amount());
            default -> { }
        }
        metrics.markEvent(event.occurredAt());          // ← 반영한 이벤트 시각 기록
        metricsRepository.save(metrics);
        eventHandledRepository.save(new EventHandled(event.eventId()));

        eventPublisher.publishEvent(new RankingScoreEvent(              // 추가
                event.productId(), event.eventType(), event.occurredAt()));
    }
}
