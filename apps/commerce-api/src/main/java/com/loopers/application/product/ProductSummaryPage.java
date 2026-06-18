package com.loopers.application.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public record ProductSummaryPage(
        List<ProductSummaryInfo> content,
        long totalElements
) {
    public static ProductSummaryPage from(Page<ProductSummaryInfo> page) {
        return new ProductSummaryPage(page.getContent(), page.getTotalElements());
    }

    public Page<ProductSummaryInfo> toPage(Pageable pageable) {
        return new PageImpl<>(content, pageable, totalElements);
    }
}