package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void increaseLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    void decreaseLikeCount(@Param("id") Long id);

    List<ProductModel> findAllByIdIn(List<Long> ids);

    List<ProductModel> findAllByBrandId(Long brandId);

    Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable);

    @Modifying
    @Query("UPDATE ProductModel p SET p.deletedAt = :deletedAt WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    void softDeleteAllByBrandId(@Param("brandId") Long brandId, @Param("deletedAt") ZonedDateTime deletedAt);
}
