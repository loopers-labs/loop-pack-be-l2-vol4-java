package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.interfaces.api.brand.BrandV1Dto;

public class ProductV1Dto {

    public record ProductSummaryResponse(
            Long id,
            Long brandId,
            String name,
            Long price,
            Long likeCount
    ) {
        public static ProductSummaryResponse from(ProductSummaryInfo info) {
            return new ProductSummaryResponse(info.id(), info.brandId(), info.name(), info.price(), info.likeCount());
        }
    }

    public record ProductDetailResponse(
            Long id,
            String name,
            String description,
            Long price,
            Long likeCount,
            BrandV1Dto.BrandResponse brand
    ) {
        public static ProductDetailResponse from(ProductDetailInfo info) {
            return new ProductDetailResponse(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.price(),
                    info.likeCount(),
                    BrandV1Dto.BrandResponse.from(info.brand())
            );
        }
    }
}