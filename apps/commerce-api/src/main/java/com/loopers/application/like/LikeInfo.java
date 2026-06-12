package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;

public record LikeInfo(Long id, Long userId, Long productId) {
    public static LikeInfo from(LikeModel like) {
        return new LikeInfo(like.getId(), like.getUserId(), like.getProductId());
    }
}
