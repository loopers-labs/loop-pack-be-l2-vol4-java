package com.loopers.support.pagination;

import java.util.List;

public record PageResult<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        int normalizedSize = size <= 0 ? 20 : size;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalizedSize);
        boolean hasNext = page + 1 < totalPages;

        return new PageResult<>(items, page, normalizedSize, totalElements, totalPages, hasNext);
    }
}
