package com.loopers.domain.catalog.product;

public record ProductSearchCondition(
    Long brandId,
    ProductStatus status,
    int page,
    int size,
    ProductSortType sort
) {
    public ProductSearchCondition {
        if (page < 0) {
            page = 0;
        }
        if (size <= 0) {
            size = 20;
        }
        if (sort == null) {
            sort = ProductSortType.LATEST;
        }
    }
}
