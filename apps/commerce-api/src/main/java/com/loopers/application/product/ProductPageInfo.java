package com.loopers.application.product;

import java.util.List;

public record ProductPageInfo(
    List<ProductDisplayInfo> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
