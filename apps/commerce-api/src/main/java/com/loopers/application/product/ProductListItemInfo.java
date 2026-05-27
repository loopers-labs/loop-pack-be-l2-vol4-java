package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

/**
 * 상품 목록 아이템 (UC-03) — 상품 요약 + 식별된 User의 좋아요 여부(liked).
 * 등록·단건 응답(ProductInfo)과 책임이 달라(뷰 특화 liked 포함) 별도 표현으로 둔다.
 */
public record ProductListItemInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    String imageUrl,
    Long price,
    Integer stock,
    Long likesCount,
    boolean liked
) {
    public static ProductListItemInfo of(ProductModel product, String brandName, boolean liked) {
        return new ProductListItemInfo(
            product.getId(),
            product.getBrandId(),
            brandName,
            product.getName(),
            product.getDescription(),
            product.getImageUrl(),
            product.getPrice(),
            product.getStock(),
            product.getLikesCount(),
            liked
        );
    }
}
