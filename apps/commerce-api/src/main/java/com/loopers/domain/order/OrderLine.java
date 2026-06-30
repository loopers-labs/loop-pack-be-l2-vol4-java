package com.loopers.domain.order;

import com.loopers.domain.product.Product;

/**
 * 도메인 서비스 입력 — 주문 대상 상품(로드된 엔티티)과 수량.
 * 상품 조회(Repository)는 Application(UseCase) 책임이고, 도메인 서비스는 로드된 엔티티만 받는다.
 */
public record OrderLine(Product product, int quantity) {
}
