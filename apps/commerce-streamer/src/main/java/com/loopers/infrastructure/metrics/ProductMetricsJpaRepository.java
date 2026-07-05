package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    Optional<ProductMetrics> findByProductId(Long productId);

    // 좋아요: 스냅샷 + version 가드(최신만 반영)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, like_version, created_at, updated_at)
        VALUES (:productId, :likeCount, 0, 0, :version, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          like_count   = IF(:version > like_version, :likeCount, like_count),
          updated_at   = IF(:version > like_version, NOW(), updated_at),
          like_version = IF(:version > like_version, :version, like_version)
        """, nativeQuery = true)
    void applyLike(@Param("productId") Long productId, @Param("likeCount") long likeCount, @Param("version") long version);

    // 판매량: 누적
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, like_version, created_at, updated_at)
        VALUES (:productId, 0, :quantity, 0, 0, NOW(), NOW())
        ON DUPLICATE KEY UPDATE sales_count = sales_count + :quantity, updated_at = NOW()
        """, nativeQuery = true)
    void addSales(@Param("productId") Long productId, @Param("quantity") int quantity);

    // 조회수: 누적 (판매량과 동일 계열 — event_handled 로 중복만 차단하면 안전)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, like_version, created_at, updated_at)
        VALUES (:productId, 0, 0, 1, 0, NOW(), NOW())
        ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()
        """, nativeQuery = true)
    void addView(@Param("productId") Long productId);
}
