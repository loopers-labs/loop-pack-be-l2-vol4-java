package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductInfo;

public record ProductResponse(
    Long id, Long brandId, String name, String description, Long price, Integer stock) {
    public static ProductResponse from(ProductInfo info) {
        return new ProductResponse(
            info.id(), info.brandId(), info.name(), info.description(), info.price(), info.stock());
    }
}
