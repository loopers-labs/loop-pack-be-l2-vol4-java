package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

/**
 * 상품 상세 = 상품 + 소속 브랜드 조합 결과.
 */
public record ProductDetail(ProductModel product, BrandModel brand) {
}
