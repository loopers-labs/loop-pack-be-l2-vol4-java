package com.loopers.domain.like;

import java.util.List;

public interface LikeRepository {
    boolean existsBy(Long userId, Long productId);
    void save(Like like);
    void deleteBy(Long userId, Long productId);
    List<Like> findByUserId(Long userId);
}
