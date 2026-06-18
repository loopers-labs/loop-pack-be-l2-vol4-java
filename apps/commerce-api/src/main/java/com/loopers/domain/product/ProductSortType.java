package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum ProductSortType {
    LATEST("latest"),
    PRICE("price"),
    LIKE_COUNT("likeCount");

    private final String value;

    ProductSortType(String value) {
        this.value = value;
    }

    public static ProductSortType from(String value) {
        String normalized = value == null || value.isBlank() ? LATEST.value : value;
        for (ProductSortType sortType : values()) {
            if (sortType.value.equals(normalized)) {
                return sortType;
            }
        }
        throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 상품 정렬 값입니다.");
    }
}
