package com.loopers.domain.order;

import com.loopers.domain.product.Product;

/**
 * 주문 생성 시점에 도메인 서비스로 전달되는 한 줄(상품 + 수량).
 * Application Layer가 상품을 조회해 구성하고, OrderService가 이를 받아 협력 로직을 수행한다.
 */
public record OrderLine(Product product, int quantity) {
}
