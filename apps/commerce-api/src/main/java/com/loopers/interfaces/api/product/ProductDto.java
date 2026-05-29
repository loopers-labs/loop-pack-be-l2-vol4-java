package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.SortType;

public class ProductDto {

    public record CreateProductRequest(
        Long brandId,
        String name,
        String description,
        Long price,
        int stock
    ) {}

    // 재고는 ProductStock으로 분리 관리 — 상품 수정 시 브랜드 변경 불가와 동일하게 재고는 별도 API로 처리
    public record UpdateProductRequest(
        String name,
        String description,
        Long price
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        Long price,
        int stock,
        int likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount()
            );
        }
    }
}
