package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProductAdminV1Dto {

    public record CreateProductRequest(
        @NotNull @Min(1) Long brandId,
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Min(0) Long price,
        @NotNull @Min(0) Integer stock
    ) {}

    public record UpdateProductRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Min(0) Long price,
        @NotNull @Min(0) Integer stock
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock
    ) {
        public static ProductResponse from(ProductAdminInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.stock()
            );
        }
    }
}
