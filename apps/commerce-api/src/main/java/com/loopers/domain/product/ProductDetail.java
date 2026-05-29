package com.loopers.domain.product;

public record ProductDetail(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    long likeCount
) {
}
