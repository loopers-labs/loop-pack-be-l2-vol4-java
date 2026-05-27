package com.loopers.product.application;

import com.loopers.product.domain.Product;

public class ProductResult {

    public record Detail(
        Long id,
        Long brandId,
        String name,
        String description,
        long price
    ) {
        public static Detail from(Product product) {
            return new Detail(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
            );
        }
    }
}
