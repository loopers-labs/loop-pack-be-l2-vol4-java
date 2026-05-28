package com.loopers.domain.like.repository;

import com.loopers.domain.like.model.Like;

import java.util.List;

public interface LikeRepository {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    List<Like> findAllByUserId(Long userId);
    Like save(Like like);
    void deleteByUserIdAndProductId(Long userId, Long productId);
}
