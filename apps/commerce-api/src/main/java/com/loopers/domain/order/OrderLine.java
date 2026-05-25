package com.loopers.domain.order;

/**
 * 주문 요청 한 줄 — 어떤 상품을 몇 개. (snapshot은 placeOrder 시점에 Product/Brand에서 채운다)
 */
public record OrderLine(Long productId, int quantity) {
}
