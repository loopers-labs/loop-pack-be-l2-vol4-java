package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductResult;
import com.loopers.product.domain.ProductStatus;

public class ProductAdminV1Response {

    public record AdminDetail(
        Long id,
        Long brandId,
        String name,
        String description,
        long price,
        ProductStatus status,
        String thumbnailUrl,
        long likeCount,
        int stockQuantity
    ) {
        public static AdminDetail from(ProductResult.AdminDetail result) {
            return new AdminDetail(
                result.id(),
                result.brandId(),
                result.name(),
                result.description(),
                result.price(),
                result.status(),
                result.thumbnailUrl(),
                result.likeCount(),
                result.stockQuantity()
            );
        }
    }
}
