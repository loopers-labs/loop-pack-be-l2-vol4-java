package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDailyModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDailyModel, Long> {

    Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);

    // MySQL 벤더 특화 upsert(INSERT ... ON DUPLICATE KEY UPDATE) - JPQL로 표현할 수 없어 native 쿼리로 처리한다.
    // (product_id, metric_date) 행이 없으면 생성하고 있으면 원자적으로 증가시켜, 동시 갱신에도 정합성을 보장한다.
    // metric_date는 애플리케이션이 KST 기준으로 계산해 바인딩한다(created_at/updated_at 감사 컬럼만 DB의 NOW() 사용).
    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily "
            + "(product_id, metric_date, like_count, order_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, :metricDate, 1, 0, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()", nativeQuery = true)
    void increaseLikeCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    // MySQL 벤더 특화 upsert - GREATEST로 0 미만 감소를 방지한다.
    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily "
            + "(product_id, metric_date, like_count, order_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, :metricDate, 0, 0, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0), updated_at = NOW()", nativeQuery = true)
    void decreaseLikeCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);

    // MySQL 벤더 특화 upsert - 주문 수량만큼 원자적으로 증가시킨다.
    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily "
            + "(product_id, metric_date, like_count, order_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, :metricDate, 0, :quantity, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE order_count = order_count + :quantity, updated_at = NOW()", nativeQuery = true)
    void increaseOrderCount(
            @Param("productId") Long productId,
            @Param("metricDate") LocalDate metricDate,
            @Param("quantity") Long quantity
    );

    // MySQL 벤더 특화 upsert.
    @Modifying
    @Query(value = "INSERT INTO product_metrics_daily "
            + "(product_id, metric_date, like_count, order_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, :metricDate, 0, 0, 1, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()", nativeQuery = true)
    void increaseViewCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);
}
