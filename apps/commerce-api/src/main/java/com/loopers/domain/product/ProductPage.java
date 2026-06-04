package com.loopers.domain.product;

import java.util.List;

public record ProductPage(
    List<ProductModel> products,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
