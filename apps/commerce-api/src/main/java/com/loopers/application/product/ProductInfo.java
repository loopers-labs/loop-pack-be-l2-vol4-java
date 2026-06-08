package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;

public record ProductInfo(
    Long id,
    String name,
    int price,
    String brandName,
    int stockQuantity,
    long likeCount
) {
    public static ProductInfo from(ProductDetail detail) {
        return new ProductInfo(
            detail.id(),
            detail.name(),
            detail.price(),
            detail.brandName(),
            detail.stockQuantity(),
            detail.likeCount()
        );
    }
}
