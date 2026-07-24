package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.ProductRankingEvent;
import com.loopers.domain.ranking.RankingEventType;
import com.loopers.domain.ranking.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MetricsUpdateService {

    private final ProductMetricsRepository productMetricsRepository;
    private final RankingScorePolicy rankingScorePolicy;

    public void update(ProductRankingEvent event) {
        if (event.eventType() == RankingEventType.PRODUCT_DELETED) {
            return;
        }

        LocalDate metricDate = event.occurredAt().toLocalDate();
        ProductMetrics productMetrics = productMetricsRepository.findByMetricDateAndProductId(metricDate, event.productId())
            .orElseGet(() -> ProductMetrics.create(metricDate, event.productId()));
        double scoreDelta = rankingScorePolicy.calculateScore(event.eventType(), event.price(), event.amount());

        if (event.eventType() == RankingEventType.VIEW) {
            productMetrics.addView(scoreDelta);
        }
        if (event.eventType() == RankingEventType.LIKE) {
            productMetrics.addLike(scoreDelta);
        }
        if (event.eventType() == RankingEventType.ORDER) {
            productMetrics.addSales(event.amount(), event.price(), scoreDelta);
        }

        productMetricsRepository.save(productMetrics);
    }
}
