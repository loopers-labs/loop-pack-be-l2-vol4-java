package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 집계 갱신은 전부 단문 upsert — 행이 없으면 만들고 있으면 증감한다.
 * 여러 Consumer 스레드가 같은 상품을 동시에 집계해도 한 문장이라 원자적이다.
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count) VALUES (:productId, 1, 0, 0) "
        + "ON DUPLICATE KEY UPDATE like_count = like_count + 1", nativeQuery = true)
    void increaseLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count) VALUES (:productId, 0, 0, 0) "
        + "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0)", nativeQuery = true)
    void decreaseLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count) VALUES (:productId, 0, 0, 1) "
        + "ON DUPLICATE KEY UPDATE view_count = view_count + 1", nativeQuery = true)
    void increaseViewCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count) VALUES (:productId, 0, :quantity, 0) "
        + "ON DUPLICATE KEY UPDATE sale_count = sale_count + :quantity", nativeQuery = true)
    void increaseSaleCount(@Param("productId") Long productId, @Param("quantity") int quantity);
}
