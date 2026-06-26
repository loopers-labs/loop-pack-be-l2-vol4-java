package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductResult;
import com.loopers.product.domain.ProductDisplayStatus;

public class ProductV1Response {

    public record Detail(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        long price,
        ProductDisplayStatus displayStatus,
        String thumbnailUrl,
        long likeCount
    ) {
        public static Detail from(ProductResult.Detail result) {
            return new Detail(
                result.id(),
                result.brandId(),
                result.brandName(),
                result.name(),
                result.description(),
                result.price(),
                result.displayStatus(),
                result.thumbnailUrl(),
                result.likeCount()
            );
        }
    }
}
