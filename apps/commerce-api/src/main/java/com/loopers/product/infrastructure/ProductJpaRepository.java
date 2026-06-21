package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long>, ProductJpaRepositoryCustom {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    Optional<Product> findByIdAndStatusAndDeletedAtIsNull(Long id, ProductStatus status);
    boolean existsByIdAndStatusAndDeletedAtIsNull(Long id, ProductStatus status);
    List<Product> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.deletedAt = CURRENT_TIMESTAMP, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.brandId = :brandId AND p.deletedAt IS NULL
        """)
    int softDeleteByBrandId(@Param("brandId") Long brandId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.likeCount = p.likeCount + :delta
        WHERE p.id = :id
        """)
    int incrementLikeCount(@Param("id") Long id, @Param("delta") long delta);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE products p
        LEFT JOIN (
            SELECT product_id, COUNT(*) AS cnt
            FROM likes WHERE deleted_at IS NULL
            GROUP BY product_id
        ) t ON p.id = t.product_id
        SET p.like_count = COALESCE(t.cnt, 0)
        """, nativeQuery = true)
    int reconcileLikeCounts();
}
