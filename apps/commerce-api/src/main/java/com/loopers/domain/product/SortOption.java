package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum SortOption {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public static SortOption from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        try {
            return SortOption.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 옵션입니다: " + value);
        }
    }
}
