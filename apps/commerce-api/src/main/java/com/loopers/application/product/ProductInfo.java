package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductStock;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    BigDecimal price,
    long likeCount,
    Long stock,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductInfo from(Product product, ProductStock stock) {
        return new ProductInfo(
            product.getId(), product.getBrandId(), product.getName(), product.getPrice(),
            product.getLikeCount(), stock.getQuantity(), product.getCreatedAt(), product.getUpdatedAt()
        );
    }

    public static ProductInfo from(Product product) {
        return new ProductInfo(
            product.getId(), product.getBrandId(), product.getName(), product.getPrice(),
            product.getLikeCount(), null, product.getCreatedAt(), product.getUpdatedAt()
        );
    }
}
