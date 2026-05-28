package com.loopers.domain.common;

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
            requestedPage = DEFAULT_PAGE;
        }
        if (requestedSize <= 0) {
            requestedSize = DEFAULT_SIZE;
        }

        return new PageCriteria(requestedPage, requestedSize);
    }

    public <T> List<T> slice(List<T> items) {
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        return items.subList(fromIndex, toIndex);
    }
}
