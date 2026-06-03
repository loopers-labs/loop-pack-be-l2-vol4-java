package com.loopers.domain.product;

/**
 * 상품 목록 항목 — Product + 브랜드명 + 좋아요 여부의 도메인 협력 조합 결과 (도메인 레벨).
 * application DTO(ProductListItemInfo) 변환은 Facade가 담당한다.
 */
public record ProductListEntry(
    ProductModel product,
    String brandName,
    boolean liked
) {
}
