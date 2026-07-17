package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum ProductSortType {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        return switch (value.toLowerCase()) {
            case "latest" -> LATEST;
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> throw new CoreException(
                ErrorType.BAD_REQUEST, "지원하지 않는 정렬 기준입니다: " + value);
        };
    }
}
