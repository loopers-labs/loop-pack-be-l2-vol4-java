package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductDetailInfo;

public record ProductDetailResponse(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    long likeCount) {
    public static ProductDetailResponse from(ProductDetailInfo info) {
        return new ProductDetailResponse(
            info.id(),
            info.brandId(),
            info.brandName(),
            info.name(),
            info.description(),
            info.price(),
            info.stock(),
            info.likeCount());
    }
}
