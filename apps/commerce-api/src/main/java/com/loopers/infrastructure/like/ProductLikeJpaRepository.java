package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLikeModel, Long> {
    Optional<ProductLikeModel> findByUserLoginIdAndProductId(String userLoginId, Long productId);
}
