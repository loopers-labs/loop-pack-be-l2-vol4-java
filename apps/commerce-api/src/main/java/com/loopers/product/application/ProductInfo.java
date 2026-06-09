package com.loopers.product.application;

import com.loopers.product.domain.ProductModel;

public record ProductInfo(
    Long id, Long brandId, String name, String description, Long price, Integer stock) {

    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock());
    }
}
