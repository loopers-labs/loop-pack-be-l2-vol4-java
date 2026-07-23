package com.loopers.application.outbox;

import java.util.List;

/**
 * order-events 토픽에 발행되는 메시지 페이로드.
 * items는 결제가 확정된 주문의 상품별 수량·단가 — Consumer가 product_metrics.sales_count 증가와
 * 랭킹 점수(price × quantity) 계산에 사용한다. price는 주문 시점 단가로 고정되어 이후 가격 변동에 영향받지 않는다.
 */
record OrderEventPayload(String eventId, String eventType, Long orderId, Long userId, List<Item> items) {

    static final String ORDER_PAID = "ORDER_PAID";

    record Item(Long productId, int quantity, int price) {}
}
