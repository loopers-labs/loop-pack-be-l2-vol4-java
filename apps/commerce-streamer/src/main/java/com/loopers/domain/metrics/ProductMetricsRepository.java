package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsRepository {
    /** 좋아요: 스냅샷(likeCount)은 version 가드로, 증감(likeDelta)은 이벤트마다 누적한다. */
    void applyLike(Long productId, LocalDate date, long likeCount, long version, int likeDelta);
    void addSales(Long productId, LocalDate date, int quantity);
    void addView(Long productId, LocalDate date);
    Optional<ProductMetrics> find(Long productId, LocalDate date);
}
