package com.loopers.domain.product;

import java.util.List;

public record ProductPage(
    List<ProductModel> items,
    int page,
    int size,
    long totalCount
) {
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalCount / size);
    }
}
