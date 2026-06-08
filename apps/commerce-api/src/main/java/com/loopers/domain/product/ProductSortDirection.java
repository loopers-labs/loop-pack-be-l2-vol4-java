package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum ProductSortDirection {
    ASC("asc"),
    DESC("desc");

    private final String value;

    ProductSortDirection(String value) {
        this.value = value;
    }

    public static ProductSortDirection from(String value, ProductSortDirection defaultDirection) {
        if (value == null || value.isBlank()) {
            return defaultDirection;
        }
        for (ProductSortDirection direction : values()) {
            if (direction.value.equals(value)) {
                return direction;
            }
        }
        throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 상품 정렬 방향입니다.");
    }
}
