package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public void applyLike(Long productId, long likeCount, long version) {
        jpaRepository.applyLike(productId, likeCount, version);
    }

    @Override
    public void addSales(Long productId, int quantity) {
        jpaRepository.addSales(productId, quantity);
    }

    @Override
    public Optional<ProductMetrics> find(Long productId) {
        return jpaRepository.findByProductId(productId);
    }
}
