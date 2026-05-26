package com.loopers.infrastructure.like;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeEntity, Long> {
    Optional<LikeEntity> findByUserIdAndProductId(Long userId, Long productId);

    List<LikeEntity> findByProductIdAndDeletedAtIsNull(Long productId);
}
