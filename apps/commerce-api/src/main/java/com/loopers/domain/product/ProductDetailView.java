package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;

public record ProductDetailView(
    Product product,
    Brand brand
) {
}
