package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class ProductV1Dto {

    public record CreateProductRequest(
        Long brandId,
        @NotBlank String name,
        @Positive int price,
        @PositiveOrZero int initialStock
    ) {}

    public record UpdateProductRequest(
        @NotBlank String name,
        @Positive int price
    ) {}

    public record ProductResponse(
        Long id,
        String name,
        int price,
        String brandName,
        int stockQuantity
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.price(),
                info.brandName(),
                info.stockQuantity()
            );
        }
    }
}
