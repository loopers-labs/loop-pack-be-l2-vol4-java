package com.loopers.infrastructure.catalog.metrics;

import com.loopers.domain.catalog.metrics.ProductMetrics;
import com.loopers.domain.catalog.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        ProductMetricsJpaEntity entity = metrics.isNew()
            ? productMetricsJpaRepository.findByMetricDateAndProductId(metrics.getMetricDate(), metrics.getProductId())
                .orElseGet(() -> ProductMetricsJpaEntity.from(metrics))
            : productMetricsJpaRepository.findById(metrics.getId()).orElseGet(() -> ProductMetricsJpaEntity.from(metrics));
        entity.apply(metrics);
        return productMetricsJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<ProductMetrics> findByMetricDateAndProductId(LocalDate metricDate, Long productId) {
        return productMetricsJpaRepository.findByMetricDateAndProductId(metricDate, productId)
            .map(ProductMetricsJpaEntity::toDomain);
    }
}
