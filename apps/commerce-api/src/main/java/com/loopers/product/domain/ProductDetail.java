package com.loopers.product.domain;

/** Product + Brand + 좋아요 수를 조합한 도메인 읽기 모델. */
public record ProductDetail(
    Long productId,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    long likeCount) {}
