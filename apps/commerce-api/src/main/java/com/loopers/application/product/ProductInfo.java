package com.loopers.application.product;

import com.loopers.domain.product.ProductDetail;

import java.math.BigDecimal;

public record ProductInfo(
    Long id,
    String brandName,
    String name,
    BigDecimal price,
    Long likeCount
) {
    public static ProductInfo from(ProductDetail detail) {
        return new ProductInfo(
            detail.id(),
            detail.brandName(),
            detail.name(),
            detail.price(),
            detail.likeCount()
        );
    }
}
