package com.loopers.product.application;

import com.loopers.product.domain.ProductModel;

public record ProductSummaryInfo(Long id, String name, Long price, Integer stock, Long brandId, String brandName, Long likeCount) {

    public static ProductSummaryInfo from(ProductModel model, String brandName, Integer availableStock) {
        return new ProductSummaryInfo(
            model.getId(),
            model.getName(),
            model.getPrice(),
            availableStock,
            model.getBrandId(),
            brandName,
            model.getLikeCount()
        );
    }
}
