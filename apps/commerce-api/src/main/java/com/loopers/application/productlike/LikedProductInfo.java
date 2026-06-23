package com.loopers.application.productlike;

import com.loopers.domain.product.ProductModel;

public record LikedProductInfo(
    Long productId,
    Long brandId,
    String name,
    String description,
    Long price,
    Integer stock,
    Long likeCount
) {
    public static LikedProductInfo from(ProductModel product) {
        return new LikedProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getLikeCount()
        );
    }
}
