package com.loopers.product.application;

import com.loopers.product.domain.ProductDetail;

public record ProductDetailInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    long likeCount) {

    public static ProductDetailInfo from(ProductDetail detail) {
        return new ProductDetailInfo(
            detail.productId(),
            detail.brandId(),
            detail.brandName(),
            detail.name(),
            detail.description(),
            detail.price(),
            detail.stock(),
            detail.likeCount());
    }
}
