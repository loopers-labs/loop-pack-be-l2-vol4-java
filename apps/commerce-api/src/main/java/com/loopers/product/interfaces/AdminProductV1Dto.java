package com.loopers.product.interfaces;

import com.loopers.product.application.ProductInfo;

public class AdminProductV1Dto {

    public record CreateRequest(String name, String description, Long price, Integer stock, Long brandId) {}

    public record UpdateRequest(String name, String description, Long price, Integer stock) {}

    public record ProductResponse(Long id, String name, String description, Long price, Integer stock, Long brandId) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.brandId()
            );
        }
    }
}
