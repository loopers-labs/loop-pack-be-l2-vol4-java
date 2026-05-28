package com.loopers.infrastructure.like;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLikeJpaEntity, Long> {
    Optional<ProductLikeJpaEntity> findByUserLoginIdAndProductId(String userLoginId, Long productId);

    List<ProductLikeJpaEntity> findAllByUserLoginId(String userLoginId);
}
