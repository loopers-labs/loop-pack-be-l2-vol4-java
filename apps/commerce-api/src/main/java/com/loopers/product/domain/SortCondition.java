package com.loopers.product.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum SortCondition {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public static SortCondition from(String value) {
        return switch (value.toLowerCase()) {
            case "latest" -> LATEST;
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 기준입니다: " + value);
        };
    }
}
