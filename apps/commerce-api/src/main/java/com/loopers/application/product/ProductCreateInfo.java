package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

public record ProductCreateInfo(Long productId) {

    public static ProductCreateInfo from(ProductModel product) {
        return new ProductCreateInfo(product.getId());
    }
}
