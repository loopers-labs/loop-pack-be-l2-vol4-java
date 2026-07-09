package com.loopers.infrastructure.productmetrics;

import com.loopers.domain.productmetrics.ProductMetricsModel;
import com.loopers.domain.productmetrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public Optional<ProductMetricsModel> findByProductId(Long productId) {
        return productMetricsJpaRepository.findByProductId(productId);
    }

    @Override
    public ProductMetricsModel save(ProductMetricsModel productMetrics) {
        return productMetricsJpaRepository.save(productMetrics);
    }
}
