package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
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
    public static ProductInfo from(ProductModel product, BrandModel brand, boolean available) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getBrandId(),
            brand != null ? brand.getName() : null,
            product.getLikeCount(),
            available
        );
    }
}
