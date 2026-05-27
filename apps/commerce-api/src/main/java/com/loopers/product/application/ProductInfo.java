package com.loopers.product.application;

import com.loopers.product.domain.ProductModel;

public record ProductInfo(Long id, String name, String description, Long price, Integer stock, Long brandId) {

    public static ProductInfo from(ProductModel model) {
        return new ProductInfo(
            model.getId(),
            model.getName(),
            model.getDescription(),
            model.getPrice(),
            model.getStock(),
            model.getBrandId()
        );
    }
}
