package com.loopers.application.product;

public record ProductLikeCountSnapshot(
    Long productId,
    Integer likeCount
) {
}
