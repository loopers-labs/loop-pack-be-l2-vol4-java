package com.loopers.application.order;

/**
 * 주문 항목 커맨드 — 상품 ID와 수량을 담는다.
 */
public record OrderItemCommand(Long productId, int quantity) {}
