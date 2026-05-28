package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    Optional<LikeModel> findActiveLike(Long memberId, Long productId);
    List<LikeModel> findAllActiveByMemberId(Long memberId);
    long countActiveByProductId(Long productId);
}