package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;

public record ProductInfo(
    Long id,
    String name,
    Long price,
    Long brandId,
    String brandName,
    int likeCount,
    Integer stockQuantity
) {
    public static ProductInfo from(ProductModel product, int likeCount) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getBrandId(),
            null,
            likeCount,
            null
        );
    }

    public static ProductInfo from(ProductDetail detail) {
        return new ProductInfo(
            detail.id(),
            detail.name(),
            detail.price(),
            detail.brandId(),
            detail.brandName(),
            detail.likeCount(),
            detail.stockQuantity()
        );
    }
}
