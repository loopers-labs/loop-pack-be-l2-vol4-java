package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {
    void increaseLikeCount(Long productId);

    void decreaseLikeCount(Long productId);

    void increaseViewCount(Long productId);

    void increaseSaleCount(Long productId, int quantity);

    Optional<ProductMetrics> findByProductId(Long productId);
}
