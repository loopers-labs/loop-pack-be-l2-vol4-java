package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

/**
 * 상품 상세 — Product + Brand 도메인 객체의 협력 조합 결과 (도메인 레벨).
 * liked는 식별된 User의 좋아요 여부. application DTO(ProductDetailInfo) 변환은 Facade가 담당한다.
 */
public record ProductDetail(
    ProductModel product,
    BrandModel brand,
    boolean liked
) {
}
