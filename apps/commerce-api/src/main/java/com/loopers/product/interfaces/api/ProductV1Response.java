package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductDisplayStatus;
import com.loopers.product.application.ProductResult;

public class ProductV1Response {

    public record Detail(
        Long id,
        Long brandId,
        String name,
        String description,
        long price,
        ProductDisplayStatus displayStatus,
        String thumbnailUrl
    ) {
        public static Detail from(ProductResult.Detail result) {
            return new Detail(
                result.id(),
                result.brandId(),
                result.name(),
                result.description(),
                result.price(),
                result.displayStatus(),
                result.thumbnailUrl()
            );
        }
    }
}
