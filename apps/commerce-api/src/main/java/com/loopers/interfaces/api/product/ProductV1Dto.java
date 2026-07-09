package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductListItemInfo;

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

    public record ProductListItemResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        String imageUrl,
        Long price,
        Integer stock,
        Long likesCount,
        boolean liked
    ) {
        public static ProductListItemResponse from(ProductListItemInfo info) {
            return new ProductListItemResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.description(),
                info.imageUrl(),
                info.price(),
                info.stock(),
                info.likesCount(),
                info.liked()
            );
        }
    }

    public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        Long price,
        boolean inStock,
        Long likesCount,
        Long brandId,
        String brandName,
        boolean liked
    ) {
        public static ProductDetailResponse from(ProductDetailInfo info) {
            return new ProductDetailResponse(
                info.id(),
                info.name(),
                info.description(),
                info.imageUrl(),
                info.price(),
                info.inStock(),
                info.likesCount(),
                info.brandId(),
                info.brandName(),
                info.liked()
            );
        }
    }
}
