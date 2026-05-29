package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record ProductInfo(
        Long id,
        String name,
        String status,
        Long brandId,
        String brandName,
        long likeCount
) {
    public static ProductInfo from(ProductModel product, BrandModel brand, long likeCount) {
        return new ProductInfo(
                product.getId(),
                product.getName(),
                product.getStatus().getDescription(),
                product.getBrandId(),
                brand.getName(),
                likeCount
        );
    }
}
