package com.loopers.application.catalog.product;

import com.loopers.domain.catalog.product.ProductStatus;

public class ProductCommand {
    public record Create(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        ProductStatus status
    ) {
        public Create(Long brandId, String name, String description, Long price, Integer stockQuantity) {
            this(brandId, name, description, price, stockQuantity, null);
        }
    }

    public record Update(
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        ProductStatus status
    ) {
        public Update(String name, String description, Long price, Integer stockQuantity) {
            this(name, description, price, stockQuantity, null);
        }
    }
}
