package com.loopers.application.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Sort;

public enum ProductSort {
    LATEST, PRICE_ASC, LIKES_DESC;

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by("createdAt").descending();
            case PRICE_ASC -> Sort.by("price").ascending();
            case LIKES_DESC -> Sort.by("likeCount").descending();
        };
    }

    public static ProductSort from(String value) {
        return switch (value.toLowerCase()) {
            case "latest" -> LATEST;
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 정렬 기준입니다: " + value);
        };
    }
}
