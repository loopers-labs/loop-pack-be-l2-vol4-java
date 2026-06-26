package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.ZonedDateTime;

public class ProductV1Dto {

    public record CreateProductRequest(
        @Schema(example = "1") String brandId,
        @Schema(example = "에어맥스 90") String name,
        @Schema(example = "나이키의 대표 클래식 운동화") String description,
        @Schema(example = "159000") Long price,
        @Schema(example = "100") Integer quantity
    ) {}

    public record UpdateProductRequest(
        @Schema(example = "에어맥스 90 리미티드") String name,
        @Schema(example = "한정판 에어맥스 90") String description,
        @Schema(example = "199000") Long price,
        @Schema(example = "30") Integer quantity
    ) {}

    public record CreateProductResponse(String id) {}

    public record PlpResponse(
        String id,
        String brandId,
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
        String id,
        String brandId,
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
        String id,
        String brandId,
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
        String id,
        String brandId,
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
