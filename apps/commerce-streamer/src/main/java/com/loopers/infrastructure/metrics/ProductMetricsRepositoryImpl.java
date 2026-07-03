package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
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
    public void increaseLikeCount(Long productId) {
        productMetricsJpaRepository.increaseLikeCount(productId);
    }

    @Override
    public void decreaseLikeCount(Long productId) {
        productMetricsJpaRepository.decreaseLikeCount(productId);
    }

    @Override
    public void increaseSalesCount(Long productId, Long quantity) {
        productMetricsJpaRepository.increaseSalesCount(productId, quantity);
    }

    @Override
    public void increaseViewCount(Long productId) {
        productMetricsJpaRepository.increaseViewCount(productId);
    }
}
