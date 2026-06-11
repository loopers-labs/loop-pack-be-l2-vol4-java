package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

import java.math.BigDecimal;

public record ProductInfo(
    Long id,
    String brandName,
    String name,
    BigDecimal price,
    Long likeCount,
    boolean inStock
) {
    public static ProductInfo from(ProductModel product, BrandModel brand, StockModel stock) {
        return new ProductInfo(
            product.getId(),
            brand.getName(),
            product.getName(),
            product.getPrice(),
            product.getLikeCount(),
            stock.getQuantity() > 0
        );
    }
}
