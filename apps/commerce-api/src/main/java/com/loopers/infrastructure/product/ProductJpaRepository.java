package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    List<ProductModel> findAllByBrandId(Long brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductModel> findByIdForUpdate(@Param("id") Long id);

    // LATEST / PRICE_ASC 정렬은 Pageable로 처리; 필터 파라미터 null = 조건 미적용
    @Query(value = """
        SELECT p FROM ProductModel p
        LEFT JOIN StockModel s ON p.id = s.productId
        WHERE (:brandId IS NULL OR p.brandId = :brandId)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:inStock = false OR s.quantity > 0)
        """,
        countQuery = """
        SELECT COUNT(p) FROM ProductModel p
        LEFT JOIN StockModel s ON p.id = s.productId
        WHERE (:brandId IS NULL OR p.brandId = :brandId)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:inStock = false OR s.quantity > 0)
        """)
    Page<ProductModel> findAllWithFilter(
        @Param("brandId") Long brandId,
        @Param("minPrice") Long minPrice,
        @Param("maxPrice") Long maxPrice,
        @Param("inStock") boolean inStock,
        Pageable pageable);

    // LIKES_DESC 정렬: ORDER BY 내부 고정, 필터 파라미터는 WHERE 조건
    @Query(value = """
        SELECT p FROM ProductModel p
        LEFT JOIN ProductLikeViewModel plv ON p.id = plv.productId
        LEFT JOIN StockModel s ON p.id = s.productId
        WHERE (:brandId IS NULL OR p.brandId = :brandId)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:inStock = false OR s.quantity > 0)
        ORDER BY COALESCE(plv.likeCount, 0) DESC
        """,
        countQuery = """
        SELECT COUNT(p) FROM ProductModel p
        LEFT JOIN StockModel s ON p.id = s.productId
        WHERE (:brandId IS NULL OR p.brandId = :brandId)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:inStock = false OR s.quantity > 0)
        """)
    Page<ProductModel> findAllWithFilterOrderByLikeCountDesc(
        @Param("brandId") Long brandId,
        @Param("minPrice") Long minPrice,
        @Param("maxPrice") Long maxPrice,
        @Param("inStock") boolean inStock,
        Pageable pageable);
}
