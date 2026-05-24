package com.loopers.interfaces.api.product;

import com.loopers.application.product.CreateProductCommand;
import com.loopers.application.product.ProductInfo;

import java.time.ZonedDateTime;

public class ProductAdminV1Dto {

    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        long price,
        int stockQuantity
    ) {
        public CreateProductCommand toCommand() {
            return new CreateProductCommand(brandId, name, description, price, stockQuantity);
        }
    }

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        long price,
        int stockQuantity,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.stockQuantity(),
                info.createdAt(),
                info.updatedAt(),
                info.deletedAt()
            );
        }
    }
}
