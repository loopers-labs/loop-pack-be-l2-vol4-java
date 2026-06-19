package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.ProductDetailView;
import com.loopers.domain.product.Product;

public record ProductInfo(
    Long id,
    BrandInfo brand,
    String name,
    String description,
    Long price,
    Integer stock,
    Integer likeCount
) {
    public static ProductInfo from(ProductDetailView productDetailView) {
        Product product = productDetailView.product();
        Brand brand = productDetailView.brand();
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

    public ProductInfo withLikeCount(Integer newLikeCount) {
        return new ProductInfo(
            id,
            brand,
            name,
            description,
            price,
            stock,
            newLikeCount
        );
    }

    public record BrandInfo(
        Long id,
        String name,
        String description
    ) {
        public static BrandInfo from(Brand brand) {
            return new BrandInfo(
                brand.getId(),
                brand.getName(),
                brand.getDescription()
            );
        }
    }
}
