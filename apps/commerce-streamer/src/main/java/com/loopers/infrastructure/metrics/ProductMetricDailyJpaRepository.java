package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface ProductMetricDailyJpaRepository extends JpaRepository<ProductMetricDailyJpaEntity, ProductMetricDailyId> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_daily (metric_date, product_id, view_count, like_delta_count, purchase_quantity, created_at, updated_at)
                    VALUES (:metricDate, :productId, 1, 0, 0, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void incrementViewCount(@Param("productId") String productId, @Param("metricDate") LocalDate metricDate);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_daily (metric_date, product_id, view_count, like_delta_count, purchase_quantity, created_at, updated_at)
                    VALUES (:metricDate, :productId, 0, 1, 0, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE like_delta_count = like_delta_count + 1, updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void incrementLikeDelta(@Param("productId") String productId, @Param("metricDate") LocalDate metricDate);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_daily (metric_date, product_id, view_count, like_delta_count, purchase_quantity, created_at, updated_at)
                    VALUES (:metricDate, :productId, 0, -1, 0, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE like_delta_count = like_delta_count - 1, updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void decrementLikeDelta(@Param("productId") String productId, @Param("metricDate") LocalDate metricDate);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_daily (metric_date, product_id, view_count, like_delta_count, purchase_quantity, created_at, updated_at)
                    VALUES (:metricDate, :productId, 0, 0, :amount, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE purchase_quantity = purchase_quantity + :amount, updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void incrementPurchaseQuantity(@Param("productId") String productId, @Param("metricDate") LocalDate metricDate, @Param("amount") long amount);
}
