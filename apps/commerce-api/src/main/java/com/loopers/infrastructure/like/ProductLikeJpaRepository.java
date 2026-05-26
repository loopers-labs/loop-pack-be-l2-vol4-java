package com.loopers.infrastructure.like;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLikeEntity, ProductLikeId> {
    boolean existsByIdUserIdAndIdProductId(Long userId, Long productId);
    void deleteByIdUserIdAndIdProductId(Long userId, Long productId);
    List<ProductLikeEntity> findAllByIdUserId(Long userId);
}
