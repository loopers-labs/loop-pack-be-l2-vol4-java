package com.loopers.interfaces.api;

import com.loopers.support.pagination.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
    List<T> items,
    PageInfo pageInfo
) {
    public static <T, R> PageResponse<R> from(PageResult<T> result, Function<T, R> mapper) {
        return new PageResponse<>(
            result.items().stream().map(mapper).toList(),
            new PageInfo(
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext(),
                result.page() > 0,
                result.page() == 0,
                !result.hasNext()
            )
        );
    }

    public record PageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious,
        boolean isFirst,
        boolean isLast
    ) {}
}
