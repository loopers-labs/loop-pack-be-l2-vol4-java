package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

public record ProductDetailView(
    ProductModel product,
    BrandModel brand
) {
}
