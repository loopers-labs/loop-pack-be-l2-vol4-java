package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

/**
 * 상품 상세 — Product + Brand 조합 결과 (Aggregate 간 ID 참조를 Facade에서 조립).
 * 재고는 수치를 노출하지 않고 있음/없음만 제공한다(UC-04 정책). liked는 식별된 User의 좋아요 여부.
 */
public record ProductDetailInfo(
    Long id,
    String name,
    String description,
    String imageUrl,
    Long price,
    boolean inStock,
    Long likesCount,
    Long brandId,
    String brandName,
    boolean liked
) {
    public static ProductDetailInfo of(ProductModel product, BrandModel brand, boolean liked) {
        return new ProductDetailInfo(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getImageUrl(),
            product.getPrice(),
            product.getStock() > 0,
            product.getLikesCount(),
            brand.getId(),
            brand.getName(),
            liked
        );
    }
}
