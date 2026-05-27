package com.loopers.application.product;

import com.loopers.domain.product.projection.ProductDetail;

public record ProductDetailInfo(
    Long productId,
    String name,
    String description,
    Long brandId,
    String brandName,
    Integer price,
    Boolean isAvailable,
    Integer likeCount
) {

    public static ProductDetailInfo from(ProductDetail productDetail) {
        return new ProductDetailInfo(
            productDetail.productId(),
            productDetail.name(),
            productDetail.description(),
            productDetail.brandId(),
            productDetail.brandName(),
            productDetail.price(),
            productDetail.isAvailable(),
            productDetail.likeCount()
        );
    }
}
