package com.loopers.domain.common;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext,
        long totalElements
) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
                content.stream().map(mapper).toList(),
                page,
                size,
                hasNext,
                totalElements
        );
    }
}
