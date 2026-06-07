package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum ProductSortType {

    LATEST,
    PRICE_ASC,
    LIKES_DESC;

    public static ProductSortType from(String value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정렬 기준은 필수입니다.");
        }

        try {
            return ProductSortType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정렬 기준은 latest, price_asc, likes_desc만 허용됩니다.");
        }
    }
}
