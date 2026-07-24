package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    Optional<ProductMetrics> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);

    // 좋아요: 스냅샷은 version 가드(최신만 반영), 증감(like_delta)은 항상 누적
    // — 이벤트 중복은 event_handled 가 이미 차단하므로 delta 를 무조건 더해도 안전하다.
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics
          (product_id, metric_date, like_count, like_delta, sales_count, view_count, like_version, created_at, updated_at)
        VALUES (:productId, :metricDate, :likeCount, :likeDelta, 0, 0, :version, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          like_count   = IF(:version > like_version, :likeCount, like_count),
          like_delta   = like_delta + :likeDelta,
          like_version = IF(:version > like_version, :version, like_version),
          updated_at   = NOW()
        """, nativeQuery = true)
    void applyLike(@Param("productId") Long productId,
                   @Param("metricDate") LocalDate metricDate,
                   @Param("likeCount") long likeCount,
                   @Param("version") long version,
                   @Param("likeDelta") int likeDelta);

    // 판매량: 해당 일자에 누적
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics
          (product_id, metric_date, like_count, like_delta, sales_count, view_count, like_version, created_at, updated_at)
        VALUES (:productId, :metricDate, 0, 0, :quantity, 0, 0, NOW(), NOW())
        ON DUPLICATE KEY UPDATE sales_count = sales_count + :quantity, updated_at = NOW()
        """, nativeQuery = true)
    void addSales(@Param("productId") Long productId,
                  @Param("metricDate") LocalDate metricDate,
                  @Param("quantity") int quantity);

    // 조회수: 해당 일자에 누적 (판매량과 동일 계열 — event_handled 로 중복만 차단하면 안전)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics
          (product_id, metric_date, like_count, like_delta, sales_count, view_count, like_version, created_at, updated_at)
        VALUES (:productId, :metricDate, 0, 0, 0, 1, 0, NOW(), NOW())
        ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()
        """, nativeQuery = true)
    void addView(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate);
}
