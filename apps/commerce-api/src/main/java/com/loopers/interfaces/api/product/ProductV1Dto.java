package com.loopers.interfaces.api.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductListInfo;

public class ProductV1Dto {

    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description()
            );
        }
    }

    public record ProductResponse(
        Long id,
        BrandResponse brand,
        String name,
        String description,
        long price,
        long likeCount
    ) {
        public static ProductResponse from(ProductDetailInfo info) {
            return new ProductResponse(
                info.id(),
                BrandResponse.from(info.brand()),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount()
            );
        }

        public static ProductResponse from(ProductListInfo info) {
            return new ProductResponse(
                info.id(),
                BrandResponse.from(info.brand()),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount()
            );
        }
    }
}
