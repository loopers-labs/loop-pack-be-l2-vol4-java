package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Sort;

public enum ProductSort {
    LATEST {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
    },
    PRICE_ASC {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.ASC, "price");
        }
    },
    PRICE_DESC {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.DESC, "price");
        }
    },
    LIKE_ASC {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.ASC, "likeCount");
        }
    },
    LIKE_DESC {
        @Override
        public Sort toSort() {
            return Sort.by(Sort.Direction.DESC, "likeCount");
        }
    };

    public abstract Sort toSort();

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
