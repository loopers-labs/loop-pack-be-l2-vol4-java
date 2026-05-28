package com.loopers.application.like;

import java.util.UUID;

public record LikeInfo(
    UUID productId,
    long likeCount
) {
    public static LikeInfo of(UUID productId, long likeCount) {
        return new LikeInfo(productId, likeCount);
    }
}
