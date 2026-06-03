package com.loopers.application.catalog.like;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductStatus;

public record ProductLikeResult(
    Long productId,
    String name,
    Long price,
    ProductStatus status,
    String brandName,
    Long likeCount,
    boolean liked
) {
    public static ProductLikeResult from(Product product, Brand brand, boolean liked) {
        return new ProductLikeResult(
            product.getId(),
            product.getName(),
            product.getPriceAmount(),
            product.getStatus(),
            brand.getName(),
            product.getLikeCount(),
            liked
        );
    }
}
