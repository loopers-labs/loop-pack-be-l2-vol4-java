package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

/**
 * 상품 상세 — Product + Brand + 재고 수량의 협력 조합 결과 (도메인 레벨).
 * liked는 식별된 User의 좋아요 여부. stockQuantity는 재고 Aggregate(StockModel)에서 조합한 수량(inStock 파생용).
 * application DTO(ProductDetailInfo) 변환은 Facade가 담당한다.
 */
public record ProductDetail(
    ProductModel product,
    BrandModel brand,
    boolean liked,
    int stockQuantity
) {
}
