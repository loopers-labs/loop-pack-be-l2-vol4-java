package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpa;

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        return jpa.save(metrics);
    }

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return jpa.findByProductId(productId);
    }
}
