package com.loopers.support.pagination;

import org.springframework.data.domain.Page;

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

    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }

    public static <T> PageResult<T> from(Page<?> page, List<T> content) {
        return new PageResult<>(
            content,
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }

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
