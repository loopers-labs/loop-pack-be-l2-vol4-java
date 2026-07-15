package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum ProductSortType {
    LIKES_DESC,
    LATEST;

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LIKES_DESC;
        }
        try {
            return ProductSortType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 조건입니다. (sort = " + value + ")");
        }
    }
}
