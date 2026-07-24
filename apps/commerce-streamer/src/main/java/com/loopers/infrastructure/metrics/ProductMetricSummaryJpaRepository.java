package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductMetricSummaryJpaRepository extends JpaRepository<ProductMetricSummaryJpaEntity, String> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_summary (product_id, view_count, like_count, purchase_count, created_at, updated_at)
                    VALUES (:productId, 1, 0, 0, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void incrementViewCount(@Param("productId") String productId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_summary (product_id, view_count, like_count, purchase_count, created_at, updated_at)
                    VALUES (:productId, 0, 1, 0, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void incrementLikeCount(@Param("productId") String productId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_summary (product_id, view_count, like_count, purchase_count, created_at, updated_at)
                    VALUES (:productId, 0, 0, 0, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0), updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void decrementLikeCount(@Param("productId") String productId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            value = """
                    INSERT INTO product_metric_summary (product_id, view_count, like_count, purchase_count, created_at, updated_at)
                    VALUES (:productId, 0, 0, :amount, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE purchase_count = purchase_count + :amount, updated_at = NOW()
                    """,
            nativeQuery = true
    )
    void incrementPurchaseCount(@Param("productId") String productId, @Param("amount") long amount);
}
