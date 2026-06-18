package com.loopers.application.product;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

public record CachedProductSummaryInfos(List<ProductSummaryInfo> content, long totalElements) {

    public static CachedProductSummaryInfos from(Page<ProductSummaryInfo> productSummaryInfos) {
        return new CachedProductSummaryInfos(productSummaryInfos.getContent(), productSummaryInfos.getTotalElements());
    }

    public Page<ProductSummaryInfo> toPage(int page, int size) {
        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }
}
