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

    /**
     * 판매량 delta 를 원자적으로 누적한다(좋아요와 동일 패턴). 다른 컬럼은 DEFAULT 0 이라 생략 가능.
     */
    @Modifying
    @Query(value = """
        INSERT INTO product_metrics (product_id, sales_count, updated_at)
        VALUES (:productId, :delta, NOW())
        ON DUPLICATE KEY UPDATE sales_count = sales_count + :delta, updated_at = NOW()
        """, nativeQuery = true)
    void upsertSalesDelta(@Param("productId") Long productId, @Param("delta") long delta);

    /**
     * 조회수 delta 를 원자적으로 누적한다(좋아요/판매량과 동일 패턴). 다른 컬럼은 DEFAULT 0 이라 생략 가능.
     */
    @Modifying
    @Query(value = """
        INSERT INTO product_metrics (product_id, view_count, updated_at)
        VALUES (:productId, :delta, NOW())
        ON DUPLICATE KEY UPDATE view_count = view_count + :delta, updated_at = NOW()
        """, nativeQuery = true)
    void upsertViewDelta(@Param("productId") Long productId, @Param("delta") long delta);

    /**
     * 재고 '절대 상태'를 upsert 한다. like_count 처럼 누적(+delta)이 아니라 이벤트가 실어온 값으로 '덮어쓴다'.
     *
     * <p>덮어쓰기이므로 순서역전·재전송으로 도착한 '오래된' 이벤트(더 낮은 version)가 최신 상태를
     * 되돌리면 안 된다. 아래 ON DUPLICATE KEY UPDATE 절의 최신성 가드가 그것을 막는다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, stock_quantity, stock_version, updated_at)
        VALUES (:productId, 0, 0, 0, :quantity, :version, NOW())
        ON DUPLICATE KEY UPDATE
            stock_quantity = IF(:version > stock_version, :quantity, stock_quantity),
            updated_at     = IF(:version > stock_version, NOW(), updated_at),
            stock_version  = GREATEST(stock_version, :version)
        """, nativeQuery = true)
    void upsertStockState(@Param("productId") Long productId, @Param("quantity") long quantity, @Param("version") long version);
}
