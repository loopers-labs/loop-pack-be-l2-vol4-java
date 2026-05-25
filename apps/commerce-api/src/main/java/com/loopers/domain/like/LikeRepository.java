package com.loopers.domain.like;

import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);

    /** (userId, productId) 단일 행 조회 — 활성/비활성 무관 (UNIQUE 제약, reactivate 패턴용). */
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
}
