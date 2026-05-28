package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ProductAdminV1Request {

    public record Create(
        @NotNull(message = "brandId 는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품 이름은 필수입니다.")
        @Size(max = 100, message = "상품 이름은 100자 이내여야 합니다.")
        String name,

        @Size(max = 1000, message = "상품 설명은 1000자 이내여야 합니다.")
        String description,

        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        long price,

        @Size(max = 500, message = "썸네일 URL은 500자 이내여야 합니다.")
        String thumbnailUrl,

        @PositiveOrZero(message = "초기 재고는 0 이상이어야 합니다.")
        int initialStockQuantity
    ) {
        public ProductCommand.Create toCommand() {
            return new ProductCommand.Create(brandId, name, description, price, thumbnailUrl, initialStockQuantity);
        }
    }

    public record Update(
        @NotBlank(message = "상품 이름은 필수입니다.")
        @Size(max = 100, message = "상품 이름은 100자 이내여야 합니다.")
        String name,

        @Size(max = 1000, message = "상품 설명은 1000자 이내여야 합니다.")
        String description,

        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        long price,

        @Size(max = 500, message = "썸네일 URL은 500자 이내여야 합니다.")
        String thumbnailUrl
    ) {
        public ProductCommand.Update toCommand(Long productId) {
            return new ProductCommand.Update(productId, name, description, price, thumbnailUrl);
        }
    }
}
