package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

public record ProductSummaryInfo(
        Long id,
        Long brandId,
        String name,
        Long price,
        Long likeCount
) {
    public static ProductSummaryInfo from(ProductModel product) {
        return new ProductSummaryInfo(
                product.getId(),
                product.getBrandId(),
                product.getName().value(),
                product.getPrice().value(),
                product.getLikeCount()
        );
    }
}