package com.loopers.application.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

/**
 * Spring의 Page(PageImpl)는 JSON 직렬화/역직렬화가 불안정하므로,
 * 캐시 저장용으로 필요한 값만 담아 감싼다.
 */
public record ProductListCache(
    List<ProductInfo> content,
    long totalElements,
    int page,
    int size
) {
    public static ProductListCache from(Page<ProductInfo> page) {
        return new ProductListCache(
            page.getContent(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize()
        );
    }

    public Page<ProductInfo> toPage() {
        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }
}
