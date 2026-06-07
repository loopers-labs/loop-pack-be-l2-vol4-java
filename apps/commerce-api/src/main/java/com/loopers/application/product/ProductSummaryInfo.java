package com.loopers.application.product;

import com.loopers.domain.product.projection.ProductSummary;

public record ProductSummaryInfo(
    Long productId,
    String name,
    Long brandId,
    String brandName,
    Integer price,
    Boolean isAvailable,
    Integer likeCount
) {

    public static ProductSummaryInfo from(ProductSummary productSummary) {
        return new ProductSummaryInfo(
            productSummary.productId(),
            productSummary.name(),
            productSummary.brandId(),
            productSummary.brandName(),
            productSummary.price(),
            productSummary.isAvailable(),
            productSummary.likeCount()
        );
    }
}
