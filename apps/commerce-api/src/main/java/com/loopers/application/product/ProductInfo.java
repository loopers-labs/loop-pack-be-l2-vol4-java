package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

public record ProductInfo(
    Long id,
    String name,
    Long price,
    Long brandId,
    int likeCount
) {
    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getBrandId(),
            product.getLikeCount()
        );
    }
}
