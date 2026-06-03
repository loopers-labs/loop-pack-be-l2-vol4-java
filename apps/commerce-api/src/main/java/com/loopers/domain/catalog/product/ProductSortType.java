package com.loopers.domain.catalog.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum ProductSortType {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc");

    private final String requestValue;

    ProductSortType(String requestValue) {
        this.requestValue = requestValue;
    }

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }

        for (ProductSortType type : values()) {
            if (type.requestValue.equals(value)) {
                return type;
            }
        }

        throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 상품 정렬 조건입니다.");
    }
}
