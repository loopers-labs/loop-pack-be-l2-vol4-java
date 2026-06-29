package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    /**
     * 좋아요 delta 를 원자적으로 반영한다. 행이 없으면 생성, 있으면 누적.
     * read-modify-write 없이 DB 한 번으로 끝나 동시 소비자(concurrency=3) 환경에서도 경합/유실이 없다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at)
        VALUES (:productId, :delta, 0, 0, NOW())
        ON DUPLICATE KEY UPDATE like_count = like_count + :delta, updated_at = NOW()
        """, nativeQuery = true)
    void upsertLikeDelta(@Param("productId") Long productId, @Param("delta") long delta);
}
