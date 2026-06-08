package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.time.ZonedDateTime;

public class ProductV1Dto {

    public record RegisterRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        int initialQuantity
    ) {}

    public record UpdateRequest(
        Long brandId,
        String name,
        String description,
        Long price
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        int likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount()
            );
        }
    }

    // 어드민용 응답 (재고 수량, 날짜 포함)
    public record ProductAdminResponse(
        Long id,
        String name,
        String description,
        Long price,
        Long brandId,
        String brandName,
        int likeCount,
        int stock,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static ProductAdminResponse from(ProductInfo info) {
            return new ProductAdminResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.brandId(),
                info.brandName(),
                info.likeCount(),
                info.stockQuantity(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }

    // 고객용 응답 (brandName, inStock 포함)
    public record ProductUserResponse(
        Long id,
        String name,
        String description,
        Long price,
        String brandName,
        int likeCount,
        boolean inStock
    ) {
        public static ProductUserResponse from(ProductInfo info) {
            return new ProductUserResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.brandName(),
                info.likeCount(),
                info.inStock()
            );
        }
    }
}
