package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class ProductAdminV1Dto {

    public record ProductCreateRequest(
        @NotNull(message="소속 브랜드는 필수입니다.") Long brandId,
        @NotBlank(message = "상품 이름은 필수입니다.") String name,
        @NotNull(message = "상품 가격은 필수입니다.") @DecimalMin(value = "0", message = "상품 가격은 0원 이상이어야 합니다.") BigDecimal price,
        @NotNull(message = "상품 수량은 필수입니다.") @Min(value = 0, message = "상품 수량은 0 이상이어야 합니다.") Long stock
    ) {}

    public record ProductUpdateRequest(
        @NotNull(message="소속 브랜드는 필수입니다.") Long brandId,
        @NotBlank(message = "상품 이름은 필수입니다.") String name,
        @NotNull(message = "상품 가격은 필수입니다.") @DecimalMin(value = "0", message = "상품 가격은 0원 이상이어야 합니다.") BigDecimal price,
        @NotNull(message = "상품 수량은 필수입니다.") @Min(value = 0, message = "상품 수량은 0 이상이어야 합니다.") Long stock
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        BigDecimal price,
        long likeCount,
        Long stock,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(), info.brandId(), info.name(), info.price(), info.likeCount(), info.stock(),
                info.createdAt(), info.updatedAt()
            );
        }
    }
}
