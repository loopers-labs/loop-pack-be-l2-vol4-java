package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

public record ProductInfo(
    Long id,
    String name,
    int price,
    String brandName,
    int stockQuantity,
    long likeCount
) {
    public static ProductInfo from(ProductModel product, StockModel stock) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getBrand().getName(),
            stock.getQuantity(),
            product.getLikeCount()
        );
    }
}
