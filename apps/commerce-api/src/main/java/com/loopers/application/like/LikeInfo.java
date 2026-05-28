package com.loopers.application.like;

public record LikeInfo(
    Long productId,
    String productName,
    Long price,
    String brandName
) {
}
