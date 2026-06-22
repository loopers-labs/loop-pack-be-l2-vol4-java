package com.loopers.domain.product;

public record ProductDetail(
    Long id,
    String name,
    Long price,
    Long brandId,
    String brandName,
    int likeCount,
    int stockQuantity
) {}
