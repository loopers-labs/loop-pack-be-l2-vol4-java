package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;

public record ProductInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    long likeCount
) {
    public static ProductInfo from(ProductDetail detail) {
        return new ProductInfo(
            detail.id(),
            detail.brandId(),
            detail.brandName(),
            detail.name(),
            detail.description(),
            detail.price(),
            detail.stock(),
            detail.likeCount()
        );
    }
}
