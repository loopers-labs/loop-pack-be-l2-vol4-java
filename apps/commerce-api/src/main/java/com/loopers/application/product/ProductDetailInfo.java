package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record ProductDetailInfo(
        Long id,
        String name,
        String description,
        Long price,
        Long likeCount,
        BrandInfo brand
) {
    public static ProductDetailInfo from(ProductModel product, BrandModel brand) {
        return new ProductDetailInfo(
                product.getId(),
                product.getName().value(),
                product.getDescription().value(),
                product.getPrice().value(),
                product.getLikeCount(),
                BrandInfo.from(brand)
        );
    }
}