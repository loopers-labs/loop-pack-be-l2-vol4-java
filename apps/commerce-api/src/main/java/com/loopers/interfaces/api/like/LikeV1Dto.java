package com.loopers.interfaces.api.like;

public class LikeV1Dto {

    public record LikedResponse(
        Long productId,
        boolean liked
    ) {}
}
