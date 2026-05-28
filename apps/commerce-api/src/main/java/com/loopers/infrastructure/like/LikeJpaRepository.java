package com.loopers.infrastructure.like;

import com.loopers.domain.like.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    List<Like> findAllByUserId(Long userId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
