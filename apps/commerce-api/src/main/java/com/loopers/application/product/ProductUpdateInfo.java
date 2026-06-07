package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

public record ProductUpdateInfo(Long productId) {

    public static ProductUpdateInfo from(ProductModel product) {
        return new ProductUpdateInfo(product.getId());
    }
}
