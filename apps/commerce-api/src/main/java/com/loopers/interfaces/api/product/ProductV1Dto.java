package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {
    public record CreateProductRequest(
        String name,
        String description,
        Long price,
        Integer stock,
        Long brandId
    ) {}

    public record UpdateProductRequest(
        String name,
        String description,
        Long price,
        Integer stock
    ) {}

    public record ProductResponse(
        Long id,
        String name,
        String description,
        Long price,
        Integer stock,
        Long brandId
    ) {
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

    public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        Long price,
        Integer stock,
        Long brandId,
        String brandName,
        long likeCount
    ) {
        public static ProductDetailResponse from(ProductDetailInfo info) {
            return new ProductDetailResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.brandId(),
                info.brandName(),
                info.likeCount()
            );
        }
    }
}
