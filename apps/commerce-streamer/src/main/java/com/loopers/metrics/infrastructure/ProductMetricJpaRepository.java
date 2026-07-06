package com.loopers.metrics.infrastructure;

import com.loopers.metrics.domain.ProductMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * product_metrics 는 이벤트로만 갱신하는 read model. 이벤트가 담아 온 delta 를 그대로 증분하며
 * SSOT(order_items / likes)를 다시 읽지 않는다(self-contained). 재전달 시 중복 누적되는 best-effort 지표로,
 * 정합성 보정은 배치 reconcile 이 담당한다.
 */
public interface ProductMetricJpaRepository extends JpaRepository<ProductMetric, Long> {

    /** 판매량 증분: 결제완료 이벤트가 담은 수량만큼. */
    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, sales_count, like_count, view_count, updated_at)
            VALUES (:productId, :delta, 0, 0, NOW())
            ON DUPLICATE KEY UPDATE sales_count = sales_count + :delta, updated_at = NOW()
            """, nativeQuery = true)
    void increaseSales(@Param("productId") Long productId, @Param("delta") long delta);

    /** 좋아요 증분: 등록 +1 / 취소 -1. */
    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, sales_count, like_count, view_count, updated_at)
            VALUES (:productId, 0, :delta, 0, NOW())
            ON DUPLICATE KEY UPDATE like_count = like_count + :delta, updated_at = NOW()
            """, nativeQuery = true)
    void increaseLike(@Param("productId") Long productId, @Param("delta") long delta);

    /** 조회 증분: +1. */
    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, sales_count, like_count, view_count, updated_at)
            VALUES (:productId, 0, 0, :delta, NOW())
            ON DUPLICATE KEY UPDATE view_count = view_count + :delta, updated_at = NOW()
            """, nativeQuery = true)
    void increaseView(@Param("productId") Long productId, @Param("delta") long delta);
}
