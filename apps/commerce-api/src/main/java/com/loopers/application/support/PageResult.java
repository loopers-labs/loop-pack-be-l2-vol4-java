package com.loopers.application.support;

import java.util.List;

public record PageResult<T>(
    List<T> items,
    int page,
    int size,
    long totalElements
) {
    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }
}
