package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.util.List;

public class ProductV1Dto {

    public record ProductSummaryResponse(
            Long id,
            String name,
            long price,
            int likeCount,
            boolean isAvailable,
            Long brandId,
            String brandName,
            String imageUrl
    ) {
        public static ProductSummaryResponse from(ProductInfo info) {
            return new ProductSummaryResponse(
                    info.id(), info.name(), info.price(),
                    info.likeCount(), info.isAvailable(),
                    info.brandId(), info.brandName(), info.imageUrl()
            );
        }
    }

    public record ProductListResponse(List<ProductSummaryResponse> items) {
        public static ProductListResponse from(List<ProductInfo> infos) {
            return new ProductListResponse(
                    infos.stream().map(ProductSummaryResponse::from).toList()
            );
        }
    }

    public record ProductDetailResponse(
            Long id,
            String name,
            String description,
            long price,
            int likeCount,
            boolean isAvailable,
            Long brandId,
            String brandName,
            String imageUrl
    ) {
        public static ProductDetailResponse from(ProductInfo info) {
            return new ProductDetailResponse(
                    info.id(), info.name(), info.description(), info.price(),
                    info.likeCount(), info.isAvailable(),
                    info.brandId(), info.brandName(), info.imageUrl()
            );
        }
    }
}
