package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public class ProductDto {

    public record CreateRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        String name,
        @NotNull(message = "가격은 필수입니다.")
        Long price,
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,
        @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
        int stockQuantity
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        String name,
        @NotNull(message = "가격은 필수입니다.")
        Long price
    ) {}

    public record StockUpdateRequest(
        @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
        int quantity
    ) {}

    public record ProductResponse(Long id, String name, Long price, Long brandId, String brandName, int likeCount, Integer stockQuantity) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(info.id(), info.name(), info.price(), info.brandId(), info.brandName(), info.likeCount(), info.stockQuantity());
        }
    }

    public record ProductPageResponse(List<ProductResponse> products, long totalElements, int totalPages) {}
}
