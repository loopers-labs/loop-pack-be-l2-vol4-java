package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.product.ProductStockModel;

public record ProductInfo(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        ProductStatus status
) {
    public static ProductInfo from(ProductModel product, ProductStockModel stock) {
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                product.getName().value(),
                product.getDescription().value(),
                product.getPrice().value(),
                stock.getStock().value(),
                product.getStatus()
        );
    }
}