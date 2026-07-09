package com.loopers.interfaces.event.like;

import com.loopers.domain.like.LikeModel;

public record LikedEvent(
    Long memberId,
    Long productId
) {
    public static LikedEvent from(LikeModel like) {
        return new LikedEvent(like.getMemberId(), like.getProductId());
    }
}
