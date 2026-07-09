package com.loopers.application.product;

/**
 * 상품 상세 — Product + Brand 조합 결과 (Aggregate 간 ID 참조를 Facade에서 조립).
 * 재고는 수치를 노출하지 않고 있음/없음만 제공한다(UC-04 정책). 좋아요 수(likesCount)는 read model
 * (product_metrics)에서 조합된 값이다. liked는 식별된 User의 좋아요 여부.
 * 인스턴스는 캐시 표현(CachedProductDetail)의 toInfo 에서 조립한다.
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
}
