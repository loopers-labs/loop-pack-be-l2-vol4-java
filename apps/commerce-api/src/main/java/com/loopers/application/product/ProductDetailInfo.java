package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;

public record ProductDetailInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    int likeCount
) {
    public static ProductDetailInfo from(ProductDetail detail) {
        ProductModel product = detail.product();
        BrandModel brand = detail.brand();
        return new ProductDetailInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getLikeCount()
        );
    }
}
