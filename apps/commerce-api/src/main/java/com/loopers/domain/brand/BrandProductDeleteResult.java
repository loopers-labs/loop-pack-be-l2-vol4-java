package com.loopers.domain.brand;

import com.loopers.domain.product.Product;

import java.util.List;

public record BrandProductDeleteResult(
    Brand brand,
    List<Product> products
) {
}
