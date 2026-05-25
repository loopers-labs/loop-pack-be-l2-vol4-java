package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {
    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        String imageUrl,
        Long price,
        Integer stock
    ) {}

    public record UpdateProductRequest(
        String name,
        String description,
        String imageUrl,
        Long price,
        Integer stock
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        String imageUrl,
        Long price,
        Integer stock,
        Long likesCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.imageUrl(),
                info.price(),
                info.stock(),
                info.likesCount()
            );
        }
    }

    public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        Long price,
        Integer stock,
        Long likesCount,
        Long brandId,
        String brandName
    ) {
        public static ProductDetailResponse from(ProductDetailInfo info) {
            return new ProductDetailResponse(
                info.id(),
                info.name(),
                info.description(),
                info.imageUrl(),
                info.price(),
                info.stock(),
                info.likesCount(),
                info.brandId(),
                info.brandName()
            );
        }
    }
}
