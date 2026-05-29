package com.loopers.domain.like;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findAllByUserId(Long userId);
    void delete(Long id);
    long countByProductId(Long productId);
    Map<Long, Long> countByProductIds(Collection<Long> productIds);
}
