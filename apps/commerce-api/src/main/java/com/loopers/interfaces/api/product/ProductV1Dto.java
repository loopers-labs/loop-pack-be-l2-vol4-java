package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {
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

    public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        Long price,
        Integer stock,
        BrandResponse brand
    ) {
        public record BrandResponse(
            Long id,
            String name,
            String logoUrl
        ) {
            public static BrandResponse from(ProductDetailInfo.BrandInfo brand) {
                return new BrandResponse(brand.id(), brand.name(), brand.logoUrl());
            }
        }

        public static ProductDetailResponse from(ProductDetailInfo info) {
            return new ProductDetailResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                BrandResponse.from(info.brand())
            );
        }
    }
}
