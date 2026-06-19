package com.loopers.product.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

import java.util.Arrays;

public enum ProductSort {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc");

    private final String value;

    ProductSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ProductSort from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        return Arrays.stream(values())
            .filter(sort -> sort.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 상품 정렬 조건입니다."));
    }
}
