package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricDailyEntity;
import com.loopers.domain.metrics.ProductMetricDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricDailyRepositoryImpl implements ProductMetricDailyRepository {

    private final ProductMetricDailyJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetricDailyEntity> findByProductIdAndMetricDate(String productId, LocalDate metricDate) {
        return jpaRepository.findById(new ProductMetricDailyId(metricDate, productId)).map(this::toDomain);
    }

    @Override
    public void incrementViewCount(String productId, LocalDate metricDate) {
        jpaRepository.incrementViewCount(productId, metricDate);
    }

    @Override
    public void incrementLikeDelta(String productId, LocalDate metricDate) {
        jpaRepository.incrementLikeDelta(productId, metricDate);
    }

    @Override
    public void decrementLikeDelta(String productId, LocalDate metricDate) {
        jpaRepository.decrementLikeDelta(productId, metricDate);
    }

    @Override
    public void incrementPurchaseQuantity(String productId, LocalDate metricDate, long amount) {
        if (amount <= 0) {
            return;
        }
        jpaRepository.incrementPurchaseQuantity(productId, metricDate, amount);
    }

    private ProductMetricDailyEntity toDomain(ProductMetricDailyJpaEntity e) {
        return ProductMetricDailyEntity.reconstruct(
                e.getId().getProductId(),
                e.getId().getMetricDate(),
                e.getViewCount(),
                e.getLikeDeltaCount(),
                e.getPurchaseQuantity(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
