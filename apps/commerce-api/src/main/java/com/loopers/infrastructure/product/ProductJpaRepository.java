package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductModel, UUID> {

    Optional<ProductModel> findByIdAndDeletedAtIsNull(UUID id);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    // 원자적 카운터 — 동시 좋아요 시 Lost Update 방지 (인메모리 증감 대체)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    int decrementLikeCount(@Param("id") UUID id);

    @Query("SELECT p FROM ProductModel p WHERE p.brand.id = :brandId AND p.deletedAt IS NULL")
    List<ProductModel> findAllByBrandIdAndDeletedAtIsNull(@Param("brandId") UUID brandId);

    @Query("SELECT p FROM ProductModel p WHERE p.brand.id = :brandId AND p.deletedAt IS NULL")
    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNull(@Param("brandId") UUID brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p WHERE p.brand.id = :brandId")
    Page<ProductModel> findAllByBrandIdPaged(@Param("brandId") UUID brandId, Pageable pageable);
}
