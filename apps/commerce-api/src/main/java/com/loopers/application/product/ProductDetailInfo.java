package com.loopers.application.product;

import com.loopers.domain.product.ProductDetailService.ProductDetail;
import com.loopers.domain.product.ProductStatus;

public record ProductDetailInfo(
    Long productId,
    String productName,
    String description,
    Long price,
    Integer stock,
    Long likeCount,
    String imageUrl,
    ProductStatus status,
    Long brandId,
    String brandName,
    String brandDescription
) {
    public static ProductDetailInfo from(ProductDetail detail) {
        return new ProductDetailInfo(
            detail.product().getId(),
            detail.product().getName(),
            detail.product().getDescription(),
            detail.product().getPrice(),
            detail.product().getStock(),
            detail.product().getLikeCount(),
            detail.product().getImageUrl(),
            detail.product().getStatus(),
            detail.brand().getId(),
            detail.brand().getName(),
            detail.brand().getDescription()
        );
    }
}
