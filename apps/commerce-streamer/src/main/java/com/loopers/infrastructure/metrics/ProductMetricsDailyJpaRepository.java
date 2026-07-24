package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.metrics.ProductMetricsDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDaily, ProductMetricsDailyId> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily "
        + "(metric_date, product_id, like_count, sale_count, view_count, order_score, updated_at) "
        + "VALUES (:metricDate, :productId, :likeDelta, :saleDelta, :viewDelta, :orderScoreDelta, NOW(6)) "
        + "ON DUPLICATE KEY UPDATE "
        + "like_count = like_count + :likeDelta, "
        + "sale_count = sale_count + :saleDelta, "
        + "view_count = view_count + :viewDelta, "
        + "order_score = order_score + :orderScoreDelta, "
        + "updated_at = NOW(6)",
        nativeQuery = true)
    int upsertDailyMetrics(@Param("metricDate") LocalDate metricDate,
                           @Param("productId") Long productId,
                           @Param("likeDelta") long likeDelta,
                           @Param("saleDelta") long saleDelta,
                           @Param("viewDelta") long viewDelta,
                           @Param("orderScoreDelta") double orderScoreDelta);
}
