package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@RequiredArgsConstructor
public enum ProductSort {
    LATEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC(Sort.by(Sort.Direction.ASC, "price")),
    PRICE_DESC(Sort.by(Sort.Direction.DESC, "price")),
    LIKE_ASC(Sort.by(Sort.Direction.ASC, "likeCount")),
    LIKE_DESC(Sort.by(Sort.Direction.DESC, "likeCount"));

    private final Sort sort;

    public Sort toSort() {
        return sort;
    }

    public static ProductSort from(String value) {
        if (value == null) {
            return LATEST;
        }
        return switch (value.toLowerCase()) {
            case "latest" -> LATEST;
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            case "like_asc" -> LIKE_ASC;
            case "like_desc" -> LIKE_DESC;
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "알 수 없는 정렬 기준입니다: " + value);
        };
    }
}
