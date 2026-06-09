package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

import java.time.ZonedDateTime;

public record ProductDetailInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    String imageUrl,
    long likeCount,
    ZonedDateTime createdAt
) {
    public static ProductDetailInfo from(ProductModel product, BrandModel brand, long likeCount) {
        return new ProductDetailInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            product.getImageUrl(),
            likeCount,
            product.getCreatedAt()
        );
    }
}
