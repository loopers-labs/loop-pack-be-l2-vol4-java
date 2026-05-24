package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

public record ProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    Integer stock
) {
    public static ProductInfo from(ProductModel product, StockModel stock) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            stock.getQuantity()
        );
    }
}
