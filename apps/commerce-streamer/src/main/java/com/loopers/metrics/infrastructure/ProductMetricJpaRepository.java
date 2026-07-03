package com.loopers.metrics.infrastructure;

import com.loopers.metrics.domain.ProductMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * product_metrics 는 SSOT(likes / order_items) 위의 materialized view.
 * 이벤트는 "이 상품이 바뀌었다"는 트리거일 뿐이고, 값은 SSOT 에서 재계산해 덮어쓴다(멱등).
 */
public interface ProductMetricJpaRepository extends JpaRepository<ProductMetric, Long> {

    /** SSOT 재계산: 유효한(soft-delete 안 된) 좋아요 수. */
    @Query(value = "SELECT COUNT(*) FROM likes WHERE product_id = :productId AND deleted_at IS NULL",
            nativeQuery = true)
    long countLikes(@Param("productId") Long productId);

    /** SSOT 재계산: 결제 완료(PAID)된 주문의 판매 수량 합. 결제완료 이벤트(OrderPaid)가 트리거. */
    @Query(value = """
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            WHERE oi.product_id = :productId AND o.status = 'PAID'
            """, nativeQuery = true)
    long sumOrderedQuantity(@Param("productId") Long productId);

    /** 재계산 값으로 판매량 덮어쓰기(절대값 → 재적용해도 동일 = 멱등). */
    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, sales_count, like_count, view_count, updated_at)
            VALUES (:productId, :salesCount, 0, 0, NOW())
            ON DUPLICATE KEY UPDATE sales_count = :salesCount, updated_at = NOW()
            """, nativeQuery = true)
    void setSales(@Param("productId") Long productId, @Param("salesCount") long salesCount);

    /** 재계산 값으로 좋아요 수 덮어쓰기(절대값 → 멱등). */
    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, sales_count, like_count, view_count, updated_at)
            VALUES (:productId, 0, :likeCount, 0, NOW())
            ON DUPLICATE KEY UPDATE like_count = :likeCount, updated_at = NOW()
            """, nativeQuery = true)
    void setLike(@Param("productId") Long productId, @Param("likeCount") long likeCount);

    /** 조회 수는 SSOT 가 없는 소프트 지표 → best-effort 증분(근사). */
    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, sales_count, like_count, view_count, updated_at)
            VALUES (:productId, 0, 0, :delta, NOW())
            ON DUPLICATE KEY UPDATE view_count = view_count + :delta, updated_at = NOW()
            """, nativeQuery = true)
    void increaseView(@Param("productId") Long productId, @Param("delta") long delta);
}
