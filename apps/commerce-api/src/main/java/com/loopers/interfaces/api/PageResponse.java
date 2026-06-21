package com.loopers.interfaces.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    public static <T> PageResponse<T> from(List<T> content, Pageable pageable, long totalElements) {
        int totalPages = pageable.getPageSize() == 0 ? 0 : (int) Math.ceil((double) totalElements / pageable.getPageSize());
        boolean hasNext = (long) (pageable.getPageNumber() + 1) * pageable.getPageSize() < totalElements;
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), totalElements, totalPages, hasNext);
    }
}