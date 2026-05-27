package com.loopers.product.application;

import com.loopers.product.domain.ProductModel;

public record ProductInfo(Long id, String name, String description, Long price, Integer stock, Long brandId, String brandName, Long likeCount) {

    public static ProductInfo from(ProductModel model, Integer availableStock) {
        return new ProductInfo(
            model.getId(),
            model.getName(),
            model.getDescription(),
            model.getPrice(),
            availableStock,
            model.getBrandId(),
            null,
            model.getLikeCount()
        );
    }

    public static ProductInfo from(ProductModel model, String brandName, Integer availableStock) {
        return new ProductInfo(
            model.getId(),
            model.getName(),
            model.getDescription(),
            model.getPrice(),
            availableStock,
            model.getBrandId(),
            brandName,
            model.getLikeCount()
        );
    }
}
