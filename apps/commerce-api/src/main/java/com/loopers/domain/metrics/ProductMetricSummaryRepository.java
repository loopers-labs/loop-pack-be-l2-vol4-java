package com.loopers.domain.metrics;

import java.util.List;
import java.util.Optional;

public interface ProductMetricSummaryRepository {
    Optional<ProductMetricSummaryEntity> findByProductId(String productId);
    List<ProductMetricSummaryEntity> findAllByProductIds(List<String> productIds);
    void createInitial(String productId);
}
