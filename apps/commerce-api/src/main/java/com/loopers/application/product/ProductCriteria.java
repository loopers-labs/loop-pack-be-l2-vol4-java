package com.loopers.application.product;

import com.loopers.domain.product.ProductSortType;

public final class ProductCriteria {

    private ProductCriteria() {
    }

    public record Register(Long brandId, String name, long price, int stock) {
    }

    public record Modify(Long id, String name, long price, int stock) {
    }

    public record GetAll(int page, int size, Long brandId, String sort) {

        public ProductSortType sortType() {
            return ProductSortType.from(sort);
        }
    }
}
