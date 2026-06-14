package com.loopers.product.application;

import com.loopers.brand.application.BrandInfo;
import com.loopers.brand.domain.Brand;
import com.loopers.product.domain.Product;

public record ProductListInfo(
    Long id,
    BrandInfo brand,
    String name,
    String description,
    long price,
    long likeCount
) {

    public static ProductListInfo from(Product product, Brand brand, long likeCount) {
        return new ProductListInfo(
            product.getId(),
            BrandInfo.from(brand),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            likeCount
        );
    }
}
