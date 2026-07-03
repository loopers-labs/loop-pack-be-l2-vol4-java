package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetricsModel> findByProductId(Long productId);

    void increaseLikeCount(Long productId);

    void decreaseLikeCount(Long productId);

    void increaseSalesCount(Long productId, Long quantity);

    void increaseViewCount(Long productId);
}
