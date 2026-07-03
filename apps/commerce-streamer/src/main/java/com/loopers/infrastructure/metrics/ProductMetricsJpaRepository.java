package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    Optional<ProductMetricsModel> findByProductId(Long productId);

    // MySQL 벤더 특화 upsert(INSERT ... ON DUPLICATE KEY UPDATE) - JPQL로 표현할 수 없어 native 쿼리로 처리한다.
    // 행이 없으면 생성하고 있으면 원자적으로 증가시켜, 동시 갱신에도 애플리케이션 레벨 동기화 없이 정합성을 보장한다.
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, 1, 0, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()", nativeQuery = true)
    void increaseLikeCount(@Param("productId") Long productId);

    // MySQL 벤더 특화 upsert - GREATEST로 0 미만 감소를 방지한다.
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, 0, 0, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0), updated_at = NOW()", nativeQuery = true)
    void decreaseLikeCount(@Param("productId") Long productId);

    // MySQL 벤더 특화 upsert - 주문 수량만큼 원자적으로 증가시킨다.
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, 0, :quantity, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE sales_count = sales_count + :quantity, updated_at = NOW()", nativeQuery = true)
    void increaseSalesCount(@Param("productId") Long productId, @Param("quantity") Long quantity);

    // MySQL 벤더 특화 upsert.
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, created_at, updated_at) "
            + "VALUES (:productId, 0, 0, 1, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()", nativeQuery = true)
    void increaseViewCount(@Param("productId") Long productId);
}
