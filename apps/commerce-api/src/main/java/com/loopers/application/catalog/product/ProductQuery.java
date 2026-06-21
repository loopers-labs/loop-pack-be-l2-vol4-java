package com.loopers.application.catalog.product;

import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.product.ProductSortType;
import com.loopers.domain.catalog.product.ProductStatus;

public class ProductQuery {
    public record Search(
        Long brandId,
        int page,
        int size,
        String sort,
        String userId
    ) {
        public ProductSearchCondition toCondition() {
            return new ProductSearchCondition(brandId, ProductStatus.ON_SALE, page, size, ProductSortType.from(sort));
        }
    }

    public record AdminSearch(
        Long brandId,
        int page,
        int size,
        String sort
    ) {
        public ProductSearchCondition toCondition() {
            return new ProductSearchCondition(brandId, null, page, size, ProductSortType.from(sort));
        }
    }
}
