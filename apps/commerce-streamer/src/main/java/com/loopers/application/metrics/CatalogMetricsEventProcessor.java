package com.loopers.application.metrics;

import com.loopers.infrastructure.metrics.EventHandledJpaEntity;
import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaEntity;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricHourlyJpaRepository;
import com.loopers.ranking.DailyRankingKey;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CatalogMetricsEventProcessor {

    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final ProductMetricHourlyJpaRepository productMetricHourlyJpaRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public boolean process(CatalogEventMessage event) {
        if (eventHandledJpaRepository.existsById(event.eventId())) {
            meterRegistry.counter("catalog_event_consume_total", "result", "duplicate", "eventType", event.eventType()).increment();
            return false;
        }

        ProductMetricsJpaEntity metrics = productMetricsJpaRepository.findByProductId(event.productId())
            .orElseGet(() -> ProductMetricsJpaEntity.create(event.productId()));
        metrics.applyLikeDelta(event.likeCountDelta());
        metrics.applyViewDelta(event.viewCountDelta());
        metrics.applySalesDelta(event.salesCountDelta());

        productMetricsJpaRepository.save(metrics);
        if (event.likeCountDelta() != 0 || event.viewCountDelta() != 0 || event.salesCountDelta() != 0) {
            var occurredAt = event.occurredAt().withZoneSameInstant(DailyRankingKey.ZONE_ID);
            productMetricHourlyJpaRepository.increment(
                occurredAt.toLocalDate(),
                occurredAt.getHour(),
                event.productId(),
                event.likeCountDelta(),
                event.viewCountDelta(),
                event.salesCountDelta()
            );
        }
        eventHandledJpaRepository.save(EventHandledJpaEntity.handled(event.eventId(), event.eventType()));
        meterRegistry.counter("catalog_event_consume_total", "result", "success", "eventType", event.eventType()).increment();
        return true;
    }
}
