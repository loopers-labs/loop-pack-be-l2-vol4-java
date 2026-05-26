package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;

public record ProductInfo(
    Long id,
    BrandInfo brand,
    String name,
    String description,
    Long price,
    Integer stock,
    Integer likeCount
) {
    public static ProductInfo from(ProductDetail productDetail) {
        ProductModel product = productDetail.product();
        BrandModel brand = productDetail.brand();
        return new ProductInfo(
            product.getId(),
            BrandInfo.from(brand),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getLikeCount()
        );
    }

    public record BrandInfo(
        Long id,
        String name,
        String description
    ) {
        public static BrandInfo from(BrandModel brand) {
            return new BrandInfo(
                brand.getId(),
                brand.getName(),
                brand.getDescription()
            );
        }
    }
}
