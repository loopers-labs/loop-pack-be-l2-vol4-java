package com.loopers.interfaces.api;

import com.loopers.support.pagination.PageResult;

import java.util.List;

public record PageResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    boolean first,
    boolean last
) {

    public static <T> PageResponse<T> from(PageResult<T> page) {
        return new PageResponse<>(
            page.content(),
            page.totalElements(),
            page.totalPages(),
            page.number(),
            page.size(),
            page.first(),
            page.last()
        );
    }
}
