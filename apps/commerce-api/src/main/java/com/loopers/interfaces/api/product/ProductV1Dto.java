package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loopers.application.product.ProductCreateCommand;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class ProductV1Dto {

    public record CreateProductRequest(
        @NotNull Long brandId,
        @NotBlank String name,
        @Positive int price,
        @PositiveOrZero int initialStock
    ) {
        public ProductCreateCommand toCommand() {
            return new ProductCreateCommand(brandId, name, price, initialStock);
        }
    }

    public record UpdateProductRequest(
        @NotBlank String name,
        @Positive int price
    ) {
        public ProductUpdateCommand toCommand() {
            return new ProductUpdateCommand(name, price);
        }
    }

    /**
     * rank는 상세 조회에서만 채워지는 오늘 랭킹 순위 — 순위에 없으면 null.
     * 전역 직렬화 정책(NON_NULL)과 달리 명시적으로 null을 내려 "순위 없음"을 표현한다 (요구사항).
     */
    public record ProductResponse(
        Long id,
        String name,
        int price,
        String brandName,
        int stockQuantity,
        long likeCount,
        @JsonInclude(JsonInclude.Include.ALWAYS) Long rank
    ) {
        public static ProductResponse from(ProductInfo info) {
            return of(info, null);
        }

        public static ProductResponse of(ProductInfo info, Long rank) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.price(),
                info.brandName(),
                info.stockQuantity(),
                info.likeCount(),
                rank
            );
        }
    }
}
