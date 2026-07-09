package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public Optional<ProductMetricsModel> findByProductId(Long productId) {
        return productMetricsJpaRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public ProductMetricsModel save(ProductMetricsModel model) {
        return productMetricsJpaRepository.save(model);
    }
}
