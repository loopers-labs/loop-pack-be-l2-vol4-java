package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricSummaryEntity;
import com.loopers.domain.metrics.ProductMetricSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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
    public List<ProductMetricSummaryEntity> findAllByProductIds(List<String> productIds) {
        return jpaRepository.findAllByProductIdIn(productIds).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void createInitial(String productId) {
        jpaRepository.save(new ProductMetricSummaryJpaEntity(productId, 0, 0, 0));
    }

    private ProductMetricSummaryEntity toDomain(ProductMetricSummaryJpaEntity e) {
        return ProductMetricSummaryEntity.of(
                e.getProductId(), e.getViewCount(), e.getLikeCount(), e.getPurchaseCount(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
