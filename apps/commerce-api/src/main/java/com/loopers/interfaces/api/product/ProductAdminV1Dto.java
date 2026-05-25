package com.loopers.interfaces.api.product;

import com.loopers.application.product.CreateProductCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.UpdateProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.ZonedDateTime;

public class ProductAdminV1Dto {

    public record CreateProductRequest(
        @NotNull(message = "브랜드 ID는 비어있을 수 없습니다.")
        Long brandId,

        @NotBlank(message = "상품명은 비어있을 수 없습니다.")
        String name,

        @NotBlank(message = "상품 설명은 비어있을 수 없습니다.")
        String description,

        @NotNull(message = "상품 가격은 비어있을 수 없습니다.")
        @PositiveOrZero(message = "상품 가격은 0 이상이어야 합니다.")
        Long price,

        @NotNull(message = "재고 수량은 비어있을 수 없습니다.")
        @PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
        Integer stockQuantity
    ) {
        public CreateProductCommand toCommand() {
            return new CreateProductCommand(brandId, name, description, price, stockQuantity);
        }
    }

    public record UpdateProductRequest(
        @NotBlank(message = "상품명은 비어있을 수 없습니다.")
        String name,

        @NotBlank(message = "상품 설명은 비어있을 수 없습니다.")
        String description,

        @NotNull(message = "상품 가격은 비어있을 수 없습니다.")
        @PositiveOrZero(message = "상품 가격은 0 이상이어야 합니다.")
        Long price,

        @PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
        Integer stockQuantity
    ) {
        public UpdateProductCommand toCommand(Long productId) {
            return new UpdateProductCommand(productId, name, description, price, stockQuantity);
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
