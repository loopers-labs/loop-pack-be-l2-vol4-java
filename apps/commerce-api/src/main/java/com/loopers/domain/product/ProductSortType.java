package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum ProductSortType {
    LATEST("최신순"),
    PRICE_ASC("가격 오름차순"),
    LIKES_DESC("좋아요 내림차순");


    private final String description;

    ProductSortType(String description) {
        this.description = description;
    }

    public static ProductSortType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return LATEST;
        }

        try {
            return ProductSortType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 정렬 기준입니다.");
        }
    }
}
