package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class ProductV1Dto {

    public record CreateProductRequest(
        @NotNull Long brandId,
        @NotBlank String name,
        @Positive int price,
        @PositiveOrZero int initialStock
    ) {
        public ProductCreateCommand toCommand() {
            return new ProductCreateCommand(brandId, name, price, initialStock);
        }
    }

    public record UpdateProductRequest(
        @NotBlank String name,
        @Positive int price
    ) {
        public ProductUpdateCommand toCommand() {
            return new ProductUpdateCommand(name, price);
        }
    }

    public record ProductResponse(
        Long id,
        String name,
        int price,
        String brandName,
        int stockQuantity,
        long likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.price(),
                info.brandName(),
                info.stockQuantity(),
                info.likeCount()
            );
        }
    }
}
