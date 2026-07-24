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

    /**
     * 배치 집계 upsert — 한 배치에서 productId 별로 합산한 좋아요 증감/판매량/조회수를 한 번에 반영한다.
     *
     * <p>좋아요/판매/조회를 개별 upsert 3종으로 나누지 않고 단일 쿼리로 합쳐, 배치당 상품별 DB 왕복을
     * 1회로 줄인다. 증감 의미(원자 증가, like_count 음수 방지)는 기존과 동일하다.
     */
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sale_count, view_count, order_score, updated_at) "
        + "VALUES (:productId, GREATEST(:likeDelta, 0), :saleDelta, :viewDelta, :orderScoreDelta, NOW(6)) "
        + "ON DUPLICATE KEY UPDATE "
        + "like_count = GREATEST(like_count + :likeDelta, 0), "
        + "sale_count = sale_count + :saleDelta, "
        + "view_count = view_count + :viewDelta, "
        + "order_score = order_score + :orderScoreDelta, "
        + "updated_at = NOW(6)",
        nativeQuery = true)
    int upsertMetrics(@Param("productId") Long productId,
                      @Param("likeDelta") long likeDelta,
                      @Param("saleDelta") long saleDelta,
                      @Param("viewDelta") long viewDelta,
                      @Param("orderScoreDelta") double orderScoreDelta);
}
