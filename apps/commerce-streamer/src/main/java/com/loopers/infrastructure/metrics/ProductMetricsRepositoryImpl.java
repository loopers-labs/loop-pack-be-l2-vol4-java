package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {
    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public void increaseLikeCount(Long productId, LocalDate metricDate) {
        productMetricsJpaRepository.increaseLikeCount(productId, metricDate);
    }

    @Override
    public void decreaseLikeCount(Long productId, LocalDate metricDate) {
        productMetricsJpaRepository.decreaseLikeCount(productId, metricDate);
    }

    @Override
    public void increaseViewCount(Long productId, LocalDate metricDate) {
        productMetricsJpaRepository.increaseViewCount(productId, metricDate);
    }

    @Override
    public void increaseSaleCount(Long productId, int quantity, double orderScore, LocalDate metricDate) {
        productMetricsJpaRepository.increaseSaleCount(productId, quantity, orderScore, metricDate);
    }

    @Override
    public Optional<ProductMetrics> findByProductIdAndMetricDate(Long productId, LocalDate metricDate) {
        return productMetricsJpaRepository.findByProductIdAndMetricDate(productId, metricDate);
    }
}
