package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

/**
 * 상품 + 브랜드 + 재고 수량을 묶어 표현하는 합성 결과.
 * 고객/어드민 Facade가 동일한 조회 합성을 공유하기 위한 중간 표현.
 */
public record ProductWithDeps(ProductModel product, BrandModel brand, int stockQuantity) {
}
