package com.loopers.product.application;

import com.loopers.product.domain.ProductSummaryModel;

public record ProductSummaryInfo(Long id, String name, Long price, Integer stock, Long brandId, String brandName, Long likeCount) {

    public static ProductSummaryInfo from(ProductSummaryModel model, String brandName, Integer availableStock) {
        return new ProductSummaryInfo(
            model.id(),
            model.name(),
            model.price(),
            availableStock,
            model.brandId(),
            brandName,
            model.likeCount()
        );
    }
}
