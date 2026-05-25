package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;

public record ProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    Long brandId,
    String brandName,
    Long likeCount,
    boolean available
) {
    public static ProductInfo from(ProductModel product, boolean available) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getBrand().getId(),
            product.getBrand().getName(),
            product.getLikeCount(),
            available
        );
    }

    public static ProductInfo from(ProductDetail detail, boolean available) {
        return new ProductInfo(
            detail.product().getId(),
            detail.product().getName(),
            detail.product().getDescription(),
            detail.product().getPrice(),
            detail.brand().getId(),
            detail.brand().getName(),
            detail.product().getLikeCount(),
            available
        );
    }
}
