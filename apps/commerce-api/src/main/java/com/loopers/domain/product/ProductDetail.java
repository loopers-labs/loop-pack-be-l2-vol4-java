package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

/**
 * 상품 상세 — Product + Brand + 재고 수량의 협력 조합 결과 (도메인 레벨, <b>사용자 무관</b>).
 * stockQuantity는 재고 Aggregate(StockModel)에서 조합한 수량(inStock 파생용).
 * 좋아요 여부(liked)는 사용자별이라 이 조합에 넣지 않는다 — Facade가 캐시 결과 위에 조합한다.
 * application DTO(ProductDetailInfo) 변환은 Facade가 담당한다.
 */
public record ProductDetail(
    ProductModel product,
    BrandModel brand,
    int stockQuantity
) {
}
