package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

/**
 * 상품 상세 — Product + Brand 조합 결과 (Aggregate 간 ID 참조를 Facade에서 조립).
 */
public record ProductDetailInfo(
    Long id,
    String name,
    String description,
    String imageUrl,
    Long price,
    Integer stock,
    Long likesCount,
    Long brandId,
    String brandName
) {
    public static ProductDetailInfo of(ProductModel product, BrandModel brand) {
        return new ProductDetailInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getImageUrl(),
            product.getPrice(),
            product.getStock(),
            product.getLikesCount(),
            brand.getId(),
            brand.getName()
        );
    }
}
