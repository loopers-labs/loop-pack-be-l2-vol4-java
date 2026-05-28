package com.loopers.interfaces.api.admin.product;

import com.loopers.application.product.ProductInfo;

// TODO: 관리자 기능으로 변경될 것
public class AdminProductV1Dto {

    public record CreateProductRequest(
            Long brandId,
            String name,
            String description,
            Long price,
            Integer stock
    ) {}

    public record UpdateProductRequest(
            Long brandId,
            String name,
            String description,
            Long price,
            Integer stock
    ) {}

    public record ProductResponse(
            Long id,
            Long brandId,
            String name,
            String description,
            Long price,
            Integer stock
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.brandId(),
                    info.name(),
                    info.description(),
                    info.price(),
                    info.stock()
            );
        }
    }
}