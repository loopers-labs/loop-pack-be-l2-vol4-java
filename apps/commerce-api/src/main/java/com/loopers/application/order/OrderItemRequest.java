package com.loopers.application.order;

import java.util.UUID;

/** 주문 생성 입력 — 주문 라인(상품 + 수량) */
public record OrderItemRequest(UUID productId, int quantity) {}
