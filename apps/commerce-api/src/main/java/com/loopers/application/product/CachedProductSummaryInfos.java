package com.loopers.application.product;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

public record CachedProductPage(List<ProductSummaryInfo> content, long totalElements) {

    public static CachedProductPage from(Page<ProductSummaryInfo> page) {
        return new CachedProductPage(page.getContent(), page.getTotalElements());
    }

    public Page<ProductSummaryInfo> toPage(int page, int size) {
        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }
}
