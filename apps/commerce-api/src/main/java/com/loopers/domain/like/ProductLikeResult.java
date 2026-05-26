package com.loopers.domain.like;

public record ProductLikeResult(
    ProductLikeModel productLike,
    boolean created
) {
}
