package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

import java.math.BigDecimal;

public record ProductInfo(
    Long id,
    String name,
    String description,
    BigDecimal price,
    int stock,
    long likeCount,
    BrandSummary brand
) {
    public static ProductInfo from(Product product, Brand brand) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice().getAmount(),
            product.getStock().getQuantity(),
            product.getLikeCount(),
            BrandSummary.from(brand)
        );
    }

    public record BrandSummary(Long id, String name, String description) {
        public static BrandSummary from(Brand brand) {
            return new BrandSummary(brand.getId(), brand.getName(), brand.getDescription());
        }
    }
}
