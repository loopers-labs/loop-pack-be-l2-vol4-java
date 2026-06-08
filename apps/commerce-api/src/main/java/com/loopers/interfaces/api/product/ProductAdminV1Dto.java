package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProductAdminV1Dto {

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock
    ) {
        public static ProductResponse from(ProductInfo info) {
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

    public record CreateProductRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.") Long brandId,
        @NotBlank(message = "상품명은 필수입니다.") String name,
        @NotBlank(message = "상품 설명은 필수입니다.") String description,
        @NotNull(message = "가격은 필수입니다.") @Min(value = 0, message = "가격은 0 이상이어야 합니다.") Long price,
        @NotNull(message = "초기 재고는 필수입니다.") @Min(value = 0, message = "재고는 0 이상이어야 합니다.") Integer stock
    ) {}

    public record UpdateProductRequest(
        @NotBlank(message = "상품명은 필수입니다.") String name,
        @NotBlank(message = "상품 설명은 필수입니다.") String description,
        @NotNull(message = "가격은 필수입니다.") @Min(value = 0, message = "가격은 0 이상이어야 합니다.") Long price,
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.") Integer stock
    ) {}
}
