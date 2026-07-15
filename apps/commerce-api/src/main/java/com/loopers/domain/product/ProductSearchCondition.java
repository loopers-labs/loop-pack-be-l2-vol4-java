package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record ProductSearchCondition(
    Long brandId,
    ProductSortType sortType,
    int page,
    int size
) {
    private static final int MAX_SIZE = 100;

    public ProductSearchCondition {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
        if (sortType == null) {
            sortType = ProductSortType.LIKES_DESC;
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
