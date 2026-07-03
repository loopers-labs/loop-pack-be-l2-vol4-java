package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricJpaRepository extends JpaRepository<ProductMetricModel, Long> {

    /**
     * 상품 지표를 원자적으로 누적한다. 행이 없으면 생성(delta로 초기화), 있으면 delta만큼 증감.
     * 증분 카운터라 덧셈은 교환법칙을 만족하므로 순서에 무관하게 합이 일정하다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO product_metrics (product_id, like_count, sales_count, updated_at) "
            + "VALUES (:productId, :likeDelta, :salesDelta, NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "like_count = like_count + :likeDelta, "
            + "sales_count = sales_count + :salesDelta, "
            + "updated_at = NOW()",
        nativeQuery = true
    )
    void upsert(@Param("productId") Long productId,
                @Param("likeDelta") long likeDelta,
                @Param("salesDelta") long salesDelta);
}
