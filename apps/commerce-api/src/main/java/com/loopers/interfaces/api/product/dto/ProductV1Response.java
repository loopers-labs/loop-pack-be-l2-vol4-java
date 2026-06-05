package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.ProductInfo;

public record ProductV1Response(
    Long id,
    String name,
    String description,
    Long price,
    Long brandId,
    String brandName,
    Long likeCount,
    boolean available
) {
    public static ProductV1Response from(ProductInfo info) {
        return new ProductV1Response(
            info.id(),
            info.name(),
            info.description(),
            info.price(),
            info.brandId(),
            info.brandName(),
            info.likeCount(),
            info.available()
        );
    }
}
