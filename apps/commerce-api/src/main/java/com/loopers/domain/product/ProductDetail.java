package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;

/**
 * 상품 상세를 위한 조합 결과(read model).
 * 상품 + 브랜드 + 좋아요 수를 묶는다. 도메인 서비스(ProductDisplayService)가 조합한다.
 */
public record ProductDetail(Product product, Brand brand, long likeCount) {
}
