package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public Optional<ProductMetricsModel> find(Long productId) {
        return productMetricsJpaRepository.findById(productId);
    }

    @Override
    public ProductMetricsModel save(ProductMetricsModel metrics) {
        return productMetricsJpaRepository.save(metrics);
    }
}