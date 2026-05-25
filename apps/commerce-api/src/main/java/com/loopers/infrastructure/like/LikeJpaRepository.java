package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
}
