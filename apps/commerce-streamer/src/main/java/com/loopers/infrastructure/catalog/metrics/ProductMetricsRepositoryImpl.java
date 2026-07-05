package com.loopers.infrastructure.catalog.metrics;

import com.loopers.domain.catalog.metrics.ProductMetrics;
import com.loopers.domain.catalog.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        ProductMetricsJpaEntity entity = metrics.isNew()
            ? productMetricsJpaRepository.findByProductId(metrics.getProductId())
                .orElseGet(() -> ProductMetricsJpaEntity.from(metrics))
            : productMetricsJpaRepository.findById(metrics.getId()).orElseGet(() -> ProductMetricsJpaEntity.from(metrics));
        entity.apply(metrics);
        return productMetricsJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return productMetricsJpaRepository.findByProductId(productId).map(ProductMetricsJpaEntity::toDomain);
    }
}
