package com.loopers.application.product;

/**
 * 상품 목록 아이템 (UC-03) — 상품 요약 + 식별된 User의 좋아요 여부(liked). 좋아요 수(likesCount)는 read model
 * (product_metrics)에서 조합된 값이다. 등록·단건 응답(ProductInfo)과 책임이 달라(뷰 특화 liked 포함) 별도로 둔다.
 * 인스턴스는 캐시 표현(CachedProductListItem)의 toInfo 에서 조립한다.
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
}
