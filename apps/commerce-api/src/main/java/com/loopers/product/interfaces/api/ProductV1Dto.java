package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductCommand;
import com.loopers.product.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ProductV1Dto {

    public record CreateRequest(
        @NotNull(message = "brandId 는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품 이름은 필수입니다.")
        @Size(max = 100, message = "상품 이름은 100자 이내여야 합니다.")
        String name,

        @Size(max = 1000, message = "상품 설명은 1000자 이내여야 합니다.")
        String description,

        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        long price,

        @PositiveOrZero(message = "초기 재고는 0 이상이어야 합니다.")
        int initialStockQuantity
    ) {
        public ProductCommand.Create toCommand() {
            return new ProductCommand.Create(brandId, name, description, price, initialStockQuantity);
        }
    }

    public record UpdateRequest(
        @NotBlank(message = "상품 이름은 필수입니다.")
        @Size(max = 100, message = "상품 이름은 100자 이내여야 합니다.")
        String name,

        @Size(max = 1000, message = "상품 설명은 1000자 이내여야 합니다.")
        String description,

        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        long price
    ) {
        public ProductCommand.Update toCommand(Long productId) {
            return new ProductCommand.Update(productId, name, description, price);
        }
    }

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        long price
    ) {
        public static ProductResponse from(Product product) {
            return new ProductResponse(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
            );
        }
    }
}
