package com.loopers.application.product;

public record ProductDetailInfo(ProductInfo product, Long rank) {

    public static ProductDetailInfo of(ProductInfo product, Long rank) {
        return new ProductDetailInfo(product, rank);
    }
}
