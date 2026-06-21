package com.loopers.application.catalog.like;

public class ProductLikeCommand {
    public record Like(
        String userId,
        Long productId
    ) {}

    public record Unlike(
        String userId,
        Long productId
    ) {}
}
