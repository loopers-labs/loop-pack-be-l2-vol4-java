package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricSummaryRepository {
    Optional<ProductMetricSummaryEntity> findByProductId(String productId);
    void incrementViewCount(String productId);
    void incrementLikeCount(String productId);
    void decrementLikeCount(String productId);
    void incrementPurchaseCount(String productId, long amount);
}
