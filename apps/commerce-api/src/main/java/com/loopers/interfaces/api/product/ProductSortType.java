package com.loopers.interfaces.api.product;

import org.springframework.data.domain.Sort;

public enum ProductSortType {

    LATEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC(Sort.by(Sort.Direction.ASC, "price.amount")),
    LIKES_DESC(Sort.by(Sort.Direction.DESC, "likeCount"));

    private final Sort sort;

    ProductSortType(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    /** null/blank/미인식 값은 LATEST로 fallback */
    public static Sort resolve(String value) {
        if (value == null || value.isBlank()) {
            return LATEST.sort;
        }
        for (ProductSortType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type.sort;
            }
        }
        return LATEST.sort;
    }
}
