package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatsModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductStatsJpaRepository extends JpaRepository<ProductStatsModel, Long> {

    Optional<ProductStatsModel> findByProduct(ProductModel product);

    List<ProductStatsModel> findAllByProductIdIn(List<Long> productIds);

    @Modifying
    @Query("UPDATE ProductStatsModel ps SET ps.likeCount = ps.likeCount + 1 WHERE ps.product.id = :productId")
    void increaseLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductStatsModel ps SET ps.likeCount = ps.likeCount - 1 WHERE ps.product.id = :productId AND ps.likeCount > 0")
    void decreaseLikeCount(@Param("productId") Long productId);
}
