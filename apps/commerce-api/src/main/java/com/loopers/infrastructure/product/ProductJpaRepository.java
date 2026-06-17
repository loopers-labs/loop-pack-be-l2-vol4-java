package com.loopers.infrastructure.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.projection.ProductAdminView;
import com.loopers.domain.product.projection.ProductDetail;
import com.loopers.domain.product.projection.ProductSummary;

import jakarta.persistence.LockModeType;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    List<ProductModel> findByBrandIdAndDeletedAtIsNull(Long brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductModel> findByIdAndDeletedAtIsNullForUpdate(Long id);

    @Transactional
    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(Long id);

    @Transactional
    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id")
    int decrementLikeCount(Long id);

    @Query(value = """
        SELECT new com.loopers.domain.product.projection.ProductSummary(
            p.id,
            p.name.value,
            b.id,
            b.name.value,
            p.price.value,
            p.stock.value,
            p.likeCount
        )
        FROM ProductModel p
        JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
        WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)
        """,
        countQuery = """
            SELECT COUNT(p.id)
            FROM ProductModel p
            JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
            WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)
            """)
    Page<ProductSummary> findActiveSummaries(Long brandId, Pageable pageable);

    @Query(value = """
        SELECT new com.loopers.domain.product.projection.ProductSummary(
            p.id,
            p.name.value,
            b.id,
            b.name.value,
            p.price.value,
            p.stock.value,
            p.likeCount
        )
        FROM ProductModel p
        JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
        WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)
        ORDER BY p.likeCount DESC, p.id DESC
        """,
        countQuery = """
            SELECT COUNT(p.id)
            FROM ProductModel p
            JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
            WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)
            """)
    Page<ProductSummary> findActiveSummariesOrderByLikeCount(Long brandId, Pageable pageable);

    @Query("""
        SELECT new com.loopers.domain.product.projection.ProductDetail(
            p.id,
            p.name.value,
            p.description,
            b.id,
            b.name.value,
            p.price.value,
            p.stock.value,
            p.likeCount
        )
        FROM ProductModel p
        JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
        WHERE p.id = :productId AND p.deletedAt IS NULL
        """)
    Optional<ProductDetail> findActiveDetailById(Long productId);

    @Query(value = """
        SELECT new com.loopers.domain.product.projection.ProductAdminView(
            p.id,
            p.name.value,
            p.description,
            b.id,
            b.name.value,
            p.price.value,
            p.stock.value,
            p.createdAt,
            p.updatedAt
        )
        FROM ProductModel p
        JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
        WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)
        ORDER BY p.createdAt DESC
        """,
        countQuery = """
            SELECT COUNT(p.id)
            FROM ProductModel p
            JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
            WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)
            """)
    Page<ProductAdminView> findActiveAdminViews(Long brandId, Pageable pageable);

    @Query("""
        SELECT new com.loopers.domain.product.projection.ProductAdminView(
            p.id,
            p.name.value,
            p.description,
            b.id,
            b.name.value,
            p.price.value,
            p.stock.value,
            p.createdAt,
            p.updatedAt
        )
        FROM ProductModel p
        JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
        WHERE p.id = :productId AND p.deletedAt IS NULL
        """)
    Optional<ProductAdminView> findActiveAdminViewById(Long productId);
}
