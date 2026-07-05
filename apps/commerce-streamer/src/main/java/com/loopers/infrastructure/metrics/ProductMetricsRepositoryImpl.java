package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public void applyLikeDelta(Long productId, long delta) {
        productMetricsJpaRepository.upsertLikeDelta(productId, delta);
    }

    @Override
    public void applySalesDelta(Long productId, long delta) {
        productMetricsJpaRepository.upsertSalesDelta(productId, delta);
    }

    @Override
    public void applyViewDelta(Long productId, long delta) {
        productMetricsJpaRepository.upsertViewDelta(productId, delta);
    }

    @Override
    public void applyStockState(Long productId, long quantity, long version) {
        productMetricsJpaRepository.upsertStockState(productId, quantity, version);
    }
}
