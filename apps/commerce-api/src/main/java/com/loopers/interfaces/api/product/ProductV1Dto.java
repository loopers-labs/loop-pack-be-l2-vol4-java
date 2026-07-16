package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {
    public record ProductResponse(
        Long id,
        String name,
        String description,
        Long price,
        boolean purchasable,
        long likeCount,
        BrandResponse brand,
        Long rank
    ) {
        public static ProductResponse from(ProductInfo info) {
            return from(info, null);
        }

        public static ProductResponse from(ProductInfo info, Long rank) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.purchasable(),
                info.likeCount(),
                BrandResponse.from(info.brand()),
                rank
            );
        }
    }

    public record BrandResponse(Long id, String name) {
        public static BrandResponse from(ProductInfo.BrandSummary summary) {
            return new BrandResponse(summary.id(), summary.name());
        }
    }
}
