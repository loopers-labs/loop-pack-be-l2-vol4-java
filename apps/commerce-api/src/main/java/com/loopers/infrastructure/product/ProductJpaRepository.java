package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    @Query("SELECT p FROM ProductModel p " +
        "WHERE p.deletedAt IS NULL " +
        "AND (:brandId IS NULL OR p.brandId = :brandId) " +
        "ORDER BY p.createdAt DESC")
    List<ProductModel> findAllByLatest(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p " +
        "WHERE p.deletedAt IS NULL " +
        "AND (:brandId IS NULL OR p.brandId = :brandId) " +
        "ORDER BY p.price ASC")
    List<ProductModel> findAllByPriceAsc(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p " +
        "LEFT JOIN com.loopers.domain.like.LikeModel l ON l.productId = p.id " +
        "WHERE p.deletedAt IS NULL " +
        "AND (:brandId IS NULL OR p.brandId = :brandId) " +
        "GROUP BY p.id " +
        "ORDER BY COUNT(l.id) DESC")
    List<ProductModel> findAllByLikesDesc(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<ProductModel> findAllByBrandId(@Param("brandId") Long brandId);

    @Query("SELECT p FROM ProductModel p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductModel> findAllByIdInAndDeletedAtIsNull(@Param("ids") List<Long> ids);
}
