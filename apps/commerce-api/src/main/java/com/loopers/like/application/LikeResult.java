package com.loopers.like.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductStatus;

public class LikeResult {

    public record LikedProduct(
        Long productId,
        Long brandId,
        String name,
        String description,
        long price,
        ProductStatus status
    ) {
        public static LikedProduct from(Product product) {
            return new LikedProduct(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus()
            );
        }
    }
}
