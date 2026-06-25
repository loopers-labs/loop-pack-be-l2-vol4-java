package com.loopers.domain.like;

public interface LikeRepository {
    boolean existsBy(Long userId, Long productId);
    Like save(Like like);
    void deleteBy(Long userId, Long productId);
}
