package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

import java.math.BigDecimal;

public record ProductAdminInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    BigDecimal price,
    Long likeCount,
    Long stock
) {
    public static ProductAdminInfo from(ProductModel product, BrandModel brand, StockModel stockModel) {
        return new ProductAdminInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getPrice(),
            product.getLikeCount(),
            stockModel.getQuantity()
        );
    }
}
