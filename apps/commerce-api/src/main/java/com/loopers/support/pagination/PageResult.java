package com.loopers.support.pagination;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    boolean first,
    boolean last
) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
            content.stream()
                .map(mapper)
                .toList(),
            totalElements,
            totalPages,
            number,
            size,
            first,
            last
        );
    }
}
