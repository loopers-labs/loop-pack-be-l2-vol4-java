package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * 멱등 삭제 — 행이 없어도 예외 없이 0 반환.
     * @return 삭제된 행 수 (0 또는 1)
     */
    int deleteByUserIdAndProductId(Long userId, Long productId);

    List<LikeModel> findAllByUserId(Long userId);
}
