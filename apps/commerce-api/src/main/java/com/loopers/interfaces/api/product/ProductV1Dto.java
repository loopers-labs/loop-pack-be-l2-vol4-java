package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

public class ProductV1Dto {

    public record CreateRequest(
        @NotNull UUID brandId,
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Min(0) Long price,
        @NotNull @Min(0) Integer initialQuantity
    ) {}

    public record UpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Min(0) Long price
    ) {}

    /** 고객용 응답 — 재고 정확한 수량 비노출, 품절 여부만 제공 */
    public record ProductResponse(
        UUID id,
        String name,
        String description,
        Long price,
        String brandName,
        long likeCount,
        String stockStatus
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.brandName(),
                info.likeCount(),
                info.availableQuantity() > 0 ? "IN_STOCK" : "OUT_OF_STOCK"
            );
        }
    }

    /** 어드민용 응답 — 정확한 재고 수량·삭제 상태·생성일 포함 */
    public record AdminProductResponse(
        UUID id,
        String name,
        String description,
        Long price,
        String brandName,
        long likeCount,
        int totalQuantity,
        int reservedQuantity,
        int availableQuantity,
        ZonedDateTime createdAt,
        ZonedDateTime deletedAt
    ) {
        public static AdminProductResponse from(ProductInfo info) {
            return new AdminProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.brandName(),
                info.likeCount(),
                info.totalQuantity(),
                info.reservedQuantity(),
                info.availableQuantity(),
                info.createdAt(),
                info.deletedAt()
            );
        }
    }
}
