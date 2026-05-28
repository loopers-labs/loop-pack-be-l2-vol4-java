package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record LikedProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    long likeCount,
    BrandSummary brand
) {
    public record BrandSummary(Long id, String name) {
        public static BrandSummary from(BrandModel brand) {
            return new BrandSummary(brand.getId(), brand.getName());
        }
    }

    public static LikedProductInfo from(ProductModel product, BrandModel brand) {
        return new LikedProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getLikeCount(),
            BrandSummary.from(brand)
        );
    }
}
