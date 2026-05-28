package com.loopers.like.domain;

import java.util.List;

public interface LikeRepository {
    LikeModel save(LikeModel like);

    boolean exists(Long memberId, Long productId);

    void delete(Long memberId, Long productId);

    long countByProductId(Long productId);

    List<LikeModel> findByMemberId(Long memberId);
}
