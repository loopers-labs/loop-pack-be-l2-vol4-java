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
        BrandResponse brand,
        boolean available,
        Long rank
    ) {
        public static ProductResponse from(ProductInfo info) {
            return from(info, null);
        }

        // 순위는 캐시되는 ProductInfo(@Cacheable) 밖에서 별도로 조회해 넘겨받는다 — 랭킹은 계속 바뀌므로 캐시에 가두면 안 된다.
        public static ProductResponse from(ProductInfo info, Long rank) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount(),
                BrandResponse.from(info.brand()),
                info.stock() > 0,
                rank
            );
        }
    }

    public record BrandResponse(Long id, String name, String description) {
        public static BrandResponse from(ProductInfo.BrandSummary brand) {
            return new BrandResponse(brand.id(), brand.name(), brand.description());
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
