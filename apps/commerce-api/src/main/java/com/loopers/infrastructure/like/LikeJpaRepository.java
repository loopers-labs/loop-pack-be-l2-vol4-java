package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findAllByUserId(Long userId);
}
