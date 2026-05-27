package com.loopers.application.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;

public record PageCriteria(
    int page,
    int size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public static PageCriteria of(Integer page, Integer size) {
        int requestedPage = page == null ? DEFAULT_PAGE : page;
        int requestedSize = size == null ? DEFAULT_SIZE : size;

        if (requestedPage < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (requestedSize < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
        }

        return new PageCriteria(requestedPage, requestedSize);
    }

    public <T> List<T> slice(List<T> items) {
        int fromIndex = page * size;
        if (fromIndex >= items.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + size, items.size());
        return items.subList(fromIndex, toIndex);
    }
}
