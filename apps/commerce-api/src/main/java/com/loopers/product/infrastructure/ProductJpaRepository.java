package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    Optional<Product> findByIdAndStatusAndDeletedAtIsNull(Long id, ProductStatus status);
    List<Product> findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(ProductStatus status);
    List<Product> findAllByStatusAndDeletedAtIsNullOrderByPriceAsc(ProductStatus status);
    List<Product> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    /**
     * 판매중 상품을 활성 좋아요 수 내림차순으로 조회한다. 좋아요 수는 매 조회마다 집계하며,
     * 트래픽이 늘어 성능이 문제가 되면 집계 컬럼이나 캐시 도입을 별도로 검토한다.
     */
    @Query(value = """
        SELECT p.* FROM products p
        LEFT JOIN likes l ON l.product_id = p.id AND l.deleted_at IS NULL
        WHERE p.status = 'ON_SALE' AND p.deleted_at IS NULL
        GROUP BY p.id
        ORDER BY COUNT(l.id) DESC, p.id DESC
        """, nativeQuery = true)
    List<Product> findOnSaleOrderByLikeCountDesc();
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Product p
        SET p.deletedAt = CURRENT_TIMESTAMP, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.brandId = :brandId AND p.deletedAt IS NULL
        """)
    int softDeleteByBrandId(@Param("brandId") Long brandId);
}
