package com.loopers.application.order;

/**
 * 주문 요청의 한 항목(상품 + 수량). interfaces 레이어의 요청 DTO와 분리된 application 입력 모델.
 */
public record OrderItemCommand(Long productId, int quantity) {
}
