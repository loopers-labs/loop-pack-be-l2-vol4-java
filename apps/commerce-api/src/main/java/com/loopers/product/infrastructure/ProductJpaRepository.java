package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT p FROM ProductModel p WHERE p.deletedAt IS NULL AND (:brandId IS NULL OR p.brandId = :brandId)")
    List<ProductModel> findAllWithFilter(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<ProductModel> findAllByIds(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM ProductModel p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<ProductModel> findAllByBrandId(@Param("brandId") Long brandId);

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);
}
