package com.loopers.infrastructure.like;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.like.LikeModel;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
