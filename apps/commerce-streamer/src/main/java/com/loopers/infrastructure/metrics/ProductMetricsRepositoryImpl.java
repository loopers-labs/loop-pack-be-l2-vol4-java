package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final DailyProductMetricsJpaRepository dailyProductMetricsJpaRepository;

    @Override
    public void applyLikeDelta(Long productId, LocalDate metricDate, long delta) {
        productMetricsJpaRepository.upsertLikeDelta(productId, delta);
        dailyProductMetricsJpaRepository.upsertLikeDelta(productId, metricDate, delta);
    }

    @Override
    public void applySalesDelta(Long productId, LocalDate metricDate, long delta) {
        productMetricsJpaRepository.upsertSalesDelta(productId, delta);
        dailyProductMetricsJpaRepository.upsertSalesDelta(productId, metricDate, delta);
    }

    @Override
    public void applyViewDelta(Long productId, LocalDate metricDate, long delta) {
        productMetricsJpaRepository.upsertViewDelta(productId, delta);
        dailyProductMetricsJpaRepository.upsertViewDelta(productId, metricDate, delta);
    }

    @Override
    public void applyStockState(Long productId, long quantity, long version) {
        productMetricsJpaRepository.upsertStockState(productId, quantity, version);
    }
}
