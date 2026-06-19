package com.loopers.domain.product;

import org.springframework.data.domain.Sort;

public enum ProductSortType {
    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public static ProductSortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        return switch (value.toLowerCase()) {
            case "price_asc" -> PRICE_ASC;
            case "likes_desc" -> LIKES_DESC;
            default -> LATEST;
        };
    }

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price.amount");
            case LIKES_DESC -> Sort.unsorted(); // 정렬은 집계 테이블 조인 쿼리(ORDER BY)에서 수행
        };
    }
}
