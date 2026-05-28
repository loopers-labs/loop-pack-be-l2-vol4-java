package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.time.ZonedDateTime;

public class ProductV1Dto {

    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer quantity
    ) {}

    public record UpdateProductRequest(
        String name,
        String description,
        Long price,
        Integer quantity
    ) {}

    public record CreateProductResponse(Long id) {}

    public record PlpResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        Long likeCount
    ) {
        public static PlpResponse from(ProductInfo info) {
            return new PlpResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount()
            );
        }
    }

    public record PdpResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        Long likeCount,
        Integer quantity,
        String description
    ) {
        public static PdpResponse from(ProductInfo info) {
            return new PdpResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.quantity(),
                info.description()
            );
        }
    }

    public record AdminPlpResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        Long likeCount,
        Integer quantity,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static AdminPlpResponse from(ProductInfo info) {
            return new AdminPlpResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.quantity(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }

    public record AdminPdpResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        Long likeCount,
        Integer quantity,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static AdminPdpResponse from(ProductInfo info) {
            return new AdminPdpResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.likeCount(),
                info.quantity(),
                info.description(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }
}
