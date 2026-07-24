package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricSummaryEntity;
import com.loopers.domain.metrics.ProductMetricSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricSummaryRepositoryImpl implements ProductMetricSummaryRepository {

    private final ProductMetricSummaryJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetricSummaryEntity> findByProductId(String productId) {
        return jpaRepository.findById(productId).map(this::toDomain);
    }

    @Override
    public void incrementViewCount(String productId) {
        jpaRepository.incrementViewCount(productId);
    }

    @Override
    public void incrementLikeCount(String productId) {
        jpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(String productId) {
        jpaRepository.decrementLikeCount(productId);
    }

    @Override
    public void incrementPurchaseCount(String productId, long amount) {
        if (amount <= 0) {
            return;
        }
        jpaRepository.incrementPurchaseCount(productId, amount);
    }

    private ProductMetricSummaryEntity toDomain(ProductMetricSummaryJpaEntity e) {
        return ProductMetricSummaryEntity.reconstruct(
                e.getProductId(), e.getViewCount(), e.getLikeCount(), e.getPurchaseCount(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
