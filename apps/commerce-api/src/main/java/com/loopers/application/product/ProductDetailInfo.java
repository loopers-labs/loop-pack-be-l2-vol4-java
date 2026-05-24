package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

public record ProductDetailInfo(
    Long id,
    BrandInfo brand,
    String name,
    String description,
    long price,
    long likeCount
) {

    public static ProductDetailInfo from(Product product, Brand brand, long likeCount) {
        return new ProductDetailInfo(
            product.getId(),
            BrandInfo.from(brand),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            likeCount
        );
    }
}
