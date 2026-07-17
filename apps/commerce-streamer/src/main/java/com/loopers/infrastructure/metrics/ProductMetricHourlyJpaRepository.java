package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricHourlyJpaRepository extends JpaRepository<ProductMetricHourlyJpaEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
        INSERT INTO product_metric_hourly
            (metric_date, metric_hour, product_id, like_count, view_count, sales_count, created_at, updated_at)
        VALUES
            (:metricDate, :metricHour, :productId, :likeCountDelta, :viewCountDelta, :salesCountDelta, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            like_count = like_count + VALUES(like_count),
            view_count = view_count + VALUES(view_count),
            sales_count = sales_count + VALUES(sales_count),
            updated_at = NOW(6)
        """, nativeQuery = true)
    int increment(
        @Param("metricDate") LocalDate metricDate,
        @Param("metricHour") int metricHour,
        @Param("productId") Long productId,
        @Param("likeCountDelta") int likeCountDelta,
        @Param("viewCountDelta") int viewCountDelta,
        @Param("salesCountDelta") int salesCountDelta
    );

    Optional<ProductMetricHourlyJpaEntity> findByMetricDateAndMetricHourAndProductId(
        LocalDate metricDate,
        int metricHour,
        Long productId
    );
}
