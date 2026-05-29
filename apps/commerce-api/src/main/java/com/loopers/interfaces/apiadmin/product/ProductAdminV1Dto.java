package com.loopers.interfaces.apiadmin.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProductAdminV1Dto {

    public record RegisterRequest(
            @NotNull(message = "브랜드 ID는 필수입니다.") Long brandId,
            @NotBlank(message = "상품명은 빈값이 들어올 수 없습니다.") String name
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "상품명은 빈값이 들어올 수 없습니다.") String name
    ) {}

    public record ProductResponse(
            Long id,
            String name,
            String status,
            Long brandId
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(info.id(), info.name(), info.status(), info.brandId());
        }
    }
}
