package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsDailyRepository {

    Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);

    // 아래 4개 메서드는 (product_id, metric_date) 행을 원자적 UPSERT로 증감한다.
    // 동시 갱신에도 애플리케이션 레벨 동기화 없이 정합성을 보장하기 위함이다.
    void increaseLikeCount(Long productId, LocalDate metricDate);

    void decreaseLikeCount(Long productId, LocalDate metricDate);

    void increaseOrderCount(Long productId, LocalDate metricDate, Long quantity);

    void increaseViewCount(Long productId, LocalDate metricDate);
}
