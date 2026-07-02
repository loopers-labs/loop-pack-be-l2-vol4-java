package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {
    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public void increaseLikeCount(Long productId) {
        productMetricsJpaRepository.increaseLikeCount(productId);
    }

    @Override
    public void decreaseLikeCount(Long productId) {
        productMetricsJpaRepository.decreaseLikeCount(productId);
    }

    @Override
    public void increaseViewCount(Long productId) {
        productMetricsJpaRepository.increaseViewCount(productId);
    }

    @Override
    public void increaseSaleCount(Long productId, int quantity) {
        productMetricsJpaRepository.increaseSaleCount(productId, quantity);
    }

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return productMetricsJpaRepository.findById(productId);
    }
}
