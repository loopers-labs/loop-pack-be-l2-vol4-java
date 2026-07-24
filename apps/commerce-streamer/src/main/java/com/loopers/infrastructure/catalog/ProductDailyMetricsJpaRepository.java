package com.loopers.infrastructure.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * product_daily_metrics upsert 전용 repository. ProductMetricsJpaRepository와 동일하게
 * INSERT ... ON DUPLICATE KEY UPDATE로 원자적 증감시킨다. 오늘 날짜(CURDATE())를 키의
 * 일부로 써서 상품×일자 단위로 누적한다.
 */
public interface ProductDailyMetricsJpaRepository
    extends JpaRepository<ProductDailyMetricsEntity, ProductDailyMetricsEntity.DailyMetricsId> {

    @Modifying
    @Query(value = """
        INSERT INTO product_daily_metrics (metric_date, product_id, order_count, like_count, view_count, updated_at)
        VALUES (CURDATE(), :productId, :delta, 0, 0, NOW())
        ON DUPLICATE KEY UPDATE order_count = order_count + :delta, updated_at = NOW()
        """, nativeQuery = true)
    void upsertOrderCount(@Param("productId") Long productId, @Param("delta") int delta);

    @Modifying
    @Query(value = """
        INSERT INTO product_daily_metrics (metric_date, product_id, order_count, like_count, view_count, updated_at)
        VALUES (CURDATE(), :productId, 0, 1, 0, NOW())
        ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()
        """, nativeQuery = true)
    void upsertLikeCountIncrement(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
        INSERT INTO product_daily_metrics (metric_date, product_id, order_count, like_count, view_count, updated_at)
        VALUES (CURDATE(), :productId, 0, 0, 0, NOW())
        ON DUPLICATE KEY UPDATE like_count = GREATEST(0, like_count - 1), updated_at = NOW()
        """, nativeQuery = true)
    // GREATEST(0, ...)로 좋아요 취소 이벤트가 좋아요 이벤트보다 먼저 도착해도 음수로 내려가지 않게 방어한다.
    void upsertLikeCountDecrement(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
        INSERT INTO product_daily_metrics (metric_date, product_id, order_count, like_count, view_count, updated_at)
        VALUES (CURDATE(), :productId, 0, 0, 1, NOW())
        ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()
        """, nativeQuery = true)
    void upsertViewCountIncrement(@Param("productId") Long productId);
}
