package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

public record ProductAdminInfo(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    Integer stock
) {
    public static ProductAdminInfo from(ProductModel product) {
        return new ProductAdminInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock()
        );
    }
}
