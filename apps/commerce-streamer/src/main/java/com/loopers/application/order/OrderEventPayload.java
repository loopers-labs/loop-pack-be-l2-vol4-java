package com.loopers.application.order;

import java.util.List;

/**
 * commerce-api의 order-events Producer가 발행하는 페이로드와 동일한 필드 계약(schema)을 갖는다.
 * price는 주문 시점 단가 — 랭킹 점수(0.6 × price × quantity) 계산에 사용한다.
 * price 필드가 없던 구버전 이벤트는 0으로 파싱되어 랭킹 점수 0으로 처리된다(가산 스킵).
 */
record OrderEventPayload(String eventId, String eventType, Long orderId, Long userId, List<Item> items) {

    static final String ORDER_PAID = "ORDER_PAID";

    record Item(Long productId, int quantity, int price) {}
}
