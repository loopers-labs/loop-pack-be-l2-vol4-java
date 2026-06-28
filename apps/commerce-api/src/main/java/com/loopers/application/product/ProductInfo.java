package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.StockStatus;

public record ProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    Integer stock,
    StockStatus stockStatus,
    Long brandId,
    Integer likeCount
) {
    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.stockStatus(),
            product.getBrandId(),
            product.getLikeCount()
        );
    }
}
