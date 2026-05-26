package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class ProductV1Dto {
    public record CreateProductRequest(
        @NotNull
        Long brandId,
        @NotBlank
        String name,
        @NotBlank
        String description,
        @NotNull
        @PositiveOrZero
        Long price,
        @NotNull
        @PositiveOrZero
        Integer stock
    ) {}

    public record UpdateProductRequest(
        @NotBlank
        String name,
        @NotBlank
        String description,
        @NotNull
        @PositiveOrZero
        Long price,
        @NotNull
        @PositiveOrZero
        Integer stock
    ) {}

    public record ProductResponse(
        Long id,
        BrandResponse brand,
        String name,
        String description,
        Long price,
        Integer stock,
        Integer likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                BrandResponse.from(info.brand()),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount()
            );
        }
    }

    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(ProductInfo.BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description()
            );
        }
    }
}
