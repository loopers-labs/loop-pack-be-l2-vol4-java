package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.support.PageResult;

import java.math.BigDecimal;
import java.util.List;

public class ProductV1Dto {

    public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        long likeCount,
        Long brandId,
        boolean available
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount(),
                info.brandId(),
                info.stock() > 0
            );
        }
    }

    public record ProductPageResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static ProductPageResponse from(PageResult<ProductInfo> page) {
            return new ProductPageResponse(
                page.items().stream().map(ProductResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
            );
        }
    }
}
