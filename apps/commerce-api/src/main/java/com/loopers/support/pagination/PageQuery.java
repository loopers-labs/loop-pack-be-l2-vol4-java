package com.loopers.support.pagination;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record PageQuery(
    int page,
    int size
) {

    public PageQuery {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
        }
    }
}
