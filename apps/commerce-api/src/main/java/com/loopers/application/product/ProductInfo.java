package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    Integer stock,
    Long likeCount,
    String imageUrl,
    ProductStatus status
) {
    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getLikeCount(),
            product.getImageUrl(),
            product.getStatus()
        );
    }
}
