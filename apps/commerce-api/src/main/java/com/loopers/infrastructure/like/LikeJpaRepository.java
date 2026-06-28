package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    List<Like> findByUserId(Long userId);
    void deleteAllByProductIdIn(List<Long> productIds);
}
