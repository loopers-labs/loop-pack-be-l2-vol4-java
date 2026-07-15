package com.loopers.interfaces.api.like;

public class LikeV1Dto {
    public record LikeResponse(
        Long productId,
        boolean liked
    ) {}
}
