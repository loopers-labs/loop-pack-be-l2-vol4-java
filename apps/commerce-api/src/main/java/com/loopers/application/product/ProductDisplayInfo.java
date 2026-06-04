package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record ProductDisplayInfo(
    Long id,
    String name,
    String description,
    Long price,
    Integer stock,
    Long likeCount,
    Long brandId,
    String brandName
) {
    public static ProductDisplayInfo of(ProductModel product, BrandModel brand) {
        return new ProductDisplayInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getLikeCount(),
            brand.getId(),
            brand.getName()
        );
    }
}
