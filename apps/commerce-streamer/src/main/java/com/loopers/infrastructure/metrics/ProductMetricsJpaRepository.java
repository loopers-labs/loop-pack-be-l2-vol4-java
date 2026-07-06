package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * product_metrics 원자 upsert.
 *
 * <p>행이 없으면 INSERT, 있으면 원자 증감(<code>ON DUPLICATE KEY UPDATE</code>) —
 * 조회 후 갱신(TOCTOU) 없이 단일 쿼리로 처리해 리스너 간 동시 갱신에도 Lost Update 가 없다.
 * like_count 는 UNLIKED(-1) 이벤트가 있어 음수 방지(GREATEST)를 적용한다.
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count, updated_at) "
        + "VALUES (:productId, GREATEST(:delta, 0), 0, 0, NOW(6)) "
        + "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count + :delta, 0), updated_at = NOW(6)",
        nativeQuery = true)
    int upsertLikeCount(@Param("productId") Long productId, @Param("delta") long delta);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count, updated_at) "
        + "VALUES (:productId, 0, :quantity, 0, NOW(6)) "
        + "ON DUPLICATE KEY UPDATE sale_count = sale_count + :quantity, updated_at = NOW(6)",
        nativeQuery = true)
    int upsertSaleCount(@Param("productId") Long productId, @Param("quantity") long quantity);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count, updated_at) "
        + "VALUES (:productId, 0, 0, 1, NOW(6)) "
        + "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW(6)",
        nativeQuery = true)
    int upsertViewCount(@Param("productId") Long productId);
}
