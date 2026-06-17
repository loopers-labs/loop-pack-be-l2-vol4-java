package com.loopers.interfaces.api.admin.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductStatus;

// TODO: 관리자 기능으로 변경될 것
public class AdminProductV1Dto {

    public record CreateProductRequest(
            Long brandId,
            String name,
            String description,
            Long price,
            Integer stock,
            ProductStatus status
    ) {}

    public record UpdateProductRequest(
            Long brandId,
            String name,
            String description,
            Long price,
            Integer stock,
            ProductStatus status
    ) {}

    public record ProductResponse(
            Long id,
            Long brandId,
            String name,
            String description,
            Long price,
            Integer stock,
            ProductStatus status
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.brandId(),
                    info.name(),
                    info.description(),
                    info.price(),
                    info.stock(),
                    info.status()
            );
        }
    }
}