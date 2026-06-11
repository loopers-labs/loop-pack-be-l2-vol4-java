package com.loopers.like.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LikeRepository {
    Like save(Like like);
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    List<Like> findActiveByUserId(Long userId);
    long countActiveByProductId(Long productId);
    Map<Long, Long> countActiveByProductIds(List<Long> productIds);
}
