package com.loopers.interfaces.api.catalog.product;

import com.loopers.application.catalog.product.ProductResult;
import com.loopers.domain.catalog.product.ProductStatus;

public class ProductAdminDto {
    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        ProductStatus status
    ) {
        public CreateProductRequest(Long brandId, String name, String description, Long price, Integer stockQuantity) {
            this(brandId, name, description, price, stockQuantity, null);
        }
    }

    public record UpdateProductRequest(
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        ProductStatus status
    ) {
        public UpdateProductRequest(String name, String description, Long price, Integer stockQuantity) {
            this(name, description, price, stockQuantity, null);
        }
    }

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        Long likeCount,
        ProductStatus status
    ) {
        public static ProductResponse from(ProductResult result) {
            return new ProductResponse(
                result.id(),
                result.brandId(),
                result.brandName(),
                result.name(),
                result.description(),
                result.price(),
                result.stockQuantity(),
                result.likeCount(),
                result.status()
            );
        }
    }
}
