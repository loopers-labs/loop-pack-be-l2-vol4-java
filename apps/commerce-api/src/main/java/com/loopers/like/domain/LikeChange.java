package com.loopers.like.domain;

public record LikeChange(
    Long productId,
    int countChangeAmount
) {

    public static LikeChange increased(Long productId) {
        return new LikeChange(productId, 1);
    }

    public static LikeChange decreased(Long productId) {
        return new LikeChange(productId, -1);
    }

    public static LikeChange unchanged(Long productId) {
        return new LikeChange(productId, 0);
    }

    public boolean hasCountChange() {
        return countChangeAmount != 0;
    }
}
