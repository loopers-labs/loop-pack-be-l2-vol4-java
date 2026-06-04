package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductDisplayInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductPageInfo;

import java.util.List;

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

    public record ProductDisplayResponse(
        Long id,
        String name,
        String description,
        Long price,
        Integer stock,
        Long likeCount,
        Long brandId,
        String brandName
    ) {
        public static ProductDisplayResponse from(ProductDisplayInfo info) {
            return new ProductDisplayResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount(),
                info.brandId(),
                info.brandName()
            );
        }
    }

    public record ProductPageResponse(
        List<ProductDisplayResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static ProductPageResponse from(ProductPageInfo info) {
            return new ProductPageResponse(
                info.content().stream()
                    .map(ProductDisplayResponse::from)
                    .toList(),
                info.page(),
                info.size(),
                info.totalElements(),
                info.totalPages()
            );
        }
    }
}
