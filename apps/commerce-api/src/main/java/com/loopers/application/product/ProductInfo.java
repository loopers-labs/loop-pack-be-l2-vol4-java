package com.loopers.application.product;

import com.loopers.domain.product.Product;

public record ProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    Integer stock,
    Long brandId
) {
    public static ProductInfo from(Product product) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice().amount().longValue(),
            product.getStock(),
            product.getBrandId()
        );
    }
}
