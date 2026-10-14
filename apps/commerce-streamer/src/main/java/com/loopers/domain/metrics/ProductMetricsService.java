package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/**
 * 집계 뷰 UPSERT — 없으면 신규 생성, 있으면 delta 반영.
 * <p>
 * 트랜잭션 안에서 처리 — save 시 UNIQUE(product_id) 충돌은 concurrent insert 로 간주하고 재조회 없이 예외 위임.
 */
@RequiredArgsConstructor
@Component
public class ProductMetricsService {

    private final ProductMetricsRepository repository;

    @Transactional
    public void applyLikeDelta(Long productId, long delta, ZonedDateTime at) {
        ProductMetrics metrics = getOrCreate(productId, at);
        metrics.applyLikeDelta(delta, at);
        repository.save(metrics);
    }

    @Transactional
    public void applyView(Long productId, ZonedDateTime at) {
        ProductMetrics metrics = getOrCreate(productId, at);
        metrics.incrementView(at);
        repository.save(metrics);
    }

    @Transactional
    public void applySales(Long productId, long quantity, ZonedDateTime at) {
        ProductMetrics metrics = getOrCreate(productId, at);
        metrics.addSales(quantity, at);
        repository.save(metrics);
    }

    @Transactional(readOnly = true)
    public ProductMetrics get(Long productId) {
        return repository.findByProductId(productId)
            .orElseThrow(() -> new IllegalStateException(
                "product_metrics missing for productId=" + productId));
    }

    private ProductMetrics getOrCreate(Long productId, ZonedDateTime at) {
        return repository.findByProductId(productId)
            .orElseGet(() -> repository.save(new ProductMetrics(productId, at)));
    }
}
