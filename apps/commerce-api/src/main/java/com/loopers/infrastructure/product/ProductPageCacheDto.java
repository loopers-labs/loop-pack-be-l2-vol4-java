package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

record ProductPageCacheDto(List<ProductCacheDto> content, long totalElements, int pageNumber, int pageSize) {

    static ProductPageCacheDto from(Page<Product> page) {
        return new ProductPageCacheDto(
            page.getContent().stream().map(ProductCacheDto::from).toList(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize()
        );
    }

    Page<Product> toDomain() {
        return new PageImpl<>(
            content.stream().map(ProductCacheDto::toDomain).toList(),
            PageRequest.of(pageNumber, pageSize),
            totalElements
        );
    }
}