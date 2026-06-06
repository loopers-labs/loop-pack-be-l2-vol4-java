package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record ProductInfo(
    Long id,
    String name,
    String description,
    Long price,
    boolean purchasable,
    long likeCount,
    BrandSummary brand
) {
    public record BrandSummary(Long id, String name) {
        public static BrandSummary from(BrandModel brand) {
            return new BrandSummary(brand.getId(), brand.getName());
        }
    }

    public static ProductInfo from(ProductModel product, boolean purchasable, BrandModel brand) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            purchasable,
            product.getLikeCount(),
            BrandSummary.from(brand)
        );
    }
}
