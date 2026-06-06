package com.loopers.interfaces.apiadmin.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ProductAdminV1Dto {

    public record RegisterRequest(
            @NotNull(message = "브랜드 ID는 필수입니다.") Long brandId,
            @NotBlank(message = "상품명은 빈값이 들어올 수 없습니다.") String name,
            @NotNull(message = "가격은 필수입니다.") @Min(value = 0, message = "가격은 0 이상이어야 합니다.") Long price,
            @NotNull(message = "수량은 필수입니다.") @Min(value = 1, message = "수량은 1 이상이어야 합니다.") Integer quantity
    ) {}

    @ValidUpdateRequest
    public record UpdateRequest(
            String name,
            Long stockId,
            @Min(value = 0, message = "가격은 0 이상이어야 합니다.") Long price,
            Integer stockQuantity
    ) {}

    public record StockResponse(Long id, Long price, Integer quantity) {}

    public record ProductResponse(
            Long id,
            String name,
            String status,
            Long brandId,
            long likeCount,
            List<StockResponse> stocks
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.name(),
                    info.status(),
                    info.brandId(),
                    info.likeCount(),
                    info.stocks().stream()
                            .map(s -> new StockResponse(s.id(), s.price(), s.quantity()))
                            .toList()
            );
        }
    }
}
