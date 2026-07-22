package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.DailyProductMetrics;
import com.loopers.domain.metrics.DailyProductMetricsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyProductMetricsJpaRepository extends JpaRepository<DailyProductMetrics, DailyProductMetricsId> {

    Optional<DailyProductMetrics> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);

    /**
     * (상품, 날짜) 행의 좋아요 delta 를 원자적으로 반영한다. 행이 없으면 생성, 있으면 누적.
     * 충돌 키가 복합(product_id, metric_date)이라 같은 상품이라도 날짜가 다르면 별개 행으로 쌓인다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO daily_product_metrics (product_id, metric_date, like_count, sales_count, view_count, updated_at)
        VALUES (:productId, :metricDate, :delta, 0, 0, NOW())
        ON DUPLICATE KEY UPDATE like_count = like_count + :delta, updated_at = NOW()
        """, nativeQuery = true)
    void upsertLikeDelta(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate, @Param("delta") long delta);

    /**
     * (상품, 날짜) 행의 판매량 delta 를 원자적으로 누적한다(좋아요와 동일 패턴).
     */
    @Modifying
    @Query(value = """
        INSERT INTO daily_product_metrics (product_id, metric_date, sales_count, updated_at)
        VALUES (:productId, :metricDate, :delta, NOW())
        ON DUPLICATE KEY UPDATE sales_count = sales_count + :delta, updated_at = NOW()
        """, nativeQuery = true)
    void upsertSalesDelta(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate, @Param("delta") long delta);

    /**
     * (상품, 날짜) 행의 조회수 delta 를 원자적으로 누적한다(좋아요/판매량과 동일 패턴).
     */
    @Modifying
    @Query(value = """
        INSERT INTO daily_product_metrics (product_id, metric_date, view_count, updated_at)
        VALUES (:productId, :metricDate, :delta, NOW())
        ON DUPLICATE KEY UPDATE view_count = view_count + :delta, updated_at = NOW()
        """, nativeQuery = true)
    void upsertViewDelta(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate, @Param("delta") long delta);
}
