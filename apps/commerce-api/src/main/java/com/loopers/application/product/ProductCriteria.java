package com.loopers.application.product;

import com.loopers.domain.product.ProductSortType;

public final class ProductCriteria {

    private ProductCriteria() {}

    public record Create(
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl
    ) {}

    public record List(
        ProductSortType sortType,
        Long brandId
    ) {}
}
