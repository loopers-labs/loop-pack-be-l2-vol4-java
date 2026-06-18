package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<ProductLikeModel, Long> {
    void deleteByUserIdAndProductId(Long userId, Long productId);
    List<ProductLikeModel> findAllByUserId(Long userId);
    Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId);
    long countByProductId(Long productId);

    @org.springframework.data.jpa.repository.Query("SELECT pl.productId, count(pl) FROM ProductLikeModel pl WHERE pl.productId IN :productIds GROUP BY pl.productId")
    List<Object[]> countByProductIds(@org.springframework.data.repository.query.Param("productIds") List<Long> productIds);
}
