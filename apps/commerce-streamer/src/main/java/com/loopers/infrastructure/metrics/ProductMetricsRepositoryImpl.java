package com.loopers.infrastructure.metrics;

import com.loopers.application.metrics.ProductMetricsRepository;
import com.loopers.domain.metrics.ProductMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public Optional<ProductMetrics> findByMetricDateAndProductId(LocalDate metricDate, Long productId) {
        return productMetricsJpaRepository.findByMetricDateAndProductId(metricDate, productId);
    }

    @Override
    public ProductMetrics save(ProductMetrics productMetrics) {
        return productMetricsJpaRepository.save(productMetrics);
    }
}
