package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 집계 갱신은 전부 단문 upsert — "그 날 그 상품" 행이 없으면 만들고 있으면 증감한다.
 * (product_id, metric_date) 유니크 키에 걸려 하루 단위로 누적되며, 여러 Consumer 스레드가 동시에 집계해도 한 문장이라 원자적이다.
 * 주문은 개수(sale_count)와 함께 정규화된 일별 주문 점수(order_score)를 같이 누적한다 — 비선형 점수를 집계 시점에 미리 확정하기 위함.
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, like_count, sale_count, view_count, order_score) "
        + "VALUES (:productId, :metricDate, 1, 0, 0, 0) "
        + "ON DUPLICATE KEY UPDATE like_count = like_count + 1", nativeQuery = true)
    void increaseLikeCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, like_count, sale_count, view_count, order_score) "
        + "VALUES (:productId, :metricDate, 0, 0, 0, 0) "
        + "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0)", nativeQuery = true)
    void decreaseLikeCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, like_count, sale_count, view_count, order_score) "
        + "VALUES (:productId, :metricDate, 0, 0, 1, 0) "
        + "ON DUPLICATE KEY UPDATE view_count = view_count + 1", nativeQuery = true)
    void increaseViewCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, like_count, sale_count, view_count, order_score) "
        + "VALUES (:productId, :metricDate, 0, :quantity, 0, :orderScore) "
        + "ON DUPLICATE KEY UPDATE sale_count = sale_count + :quantity, order_score = order_score + :orderScore", nativeQuery = true)
    void increaseSaleCount(@Param("productId") Long productId, @Param("quantity") int quantity,
                           @Param("orderScore") double orderScore, @Param("metricDate") LocalDate metricDate);

    Optional<ProductMetrics> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);
}
