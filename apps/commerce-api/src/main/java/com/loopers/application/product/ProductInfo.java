package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStock;

public record ProductInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    int stock,
    int likeCount
) {
    public static ProductInfo of(ProductModel product, ProductStock stock, Brand brand) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            stock.getStock(),
            product.getLikeCount()
        );
    }
}
