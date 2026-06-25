package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    @Modifying
    @Query("update ProductModel p set p.likeCount = p.likeCount + 1 where p.id = :id")
    int increaseLikeCount(@Param("id") Long id);

    @Modifying
    @Query("update ProductModel p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    int decreaseLikeCount(@Param("id") Long id);
}