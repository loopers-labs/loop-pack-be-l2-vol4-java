package com.loopers.infrastructure.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.projection.ProductSummary;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    @Query(value = """
        SELECT new com.loopers.domain.product.projection.ProductSummary(
            p.id,
            p.name.value,
            b.id,
            b.name.value,
            p.price.value,
            p.stock.value,
            CAST((SELECT COUNT(l.id) FROM LikeModel l WHERE l.productId = p.id) AS integer)
        )
        FROM LikeModel myLike
        JOIN ProductModel p ON p.id = myLike.productId AND p.deletedAt IS NULL
        JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
        WHERE myLike.userId = :userId
        ORDER BY myLike.createdAt DESC, myLike.id DESC
        """,
        countQuery = """
            SELECT COUNT(myLike.id)
            FROM LikeModel myLike
            JOIN ProductModel p ON p.id = myLike.productId AND p.deletedAt IS NULL
            JOIN BrandModel b ON b.id = p.brandId AND b.deletedAt IS NULL
            WHERE myLike.userId = :userId
            """)
    Page<ProductSummary> findLikedProductSummaries(Long userId, Pageable pageable);
}
