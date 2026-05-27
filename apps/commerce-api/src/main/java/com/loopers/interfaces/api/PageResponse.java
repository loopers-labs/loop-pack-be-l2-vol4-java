package com.loopers.interfaces.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
    List<T> content,
    long totalElements,
    int page,
    int size
) {
    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
            springPage.getContent(),
            springPage.getTotalElements(),
            springPage.getNumber(),
            springPage.getSize()
        );
    }
}
