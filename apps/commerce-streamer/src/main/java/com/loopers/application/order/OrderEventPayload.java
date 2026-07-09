package com.loopers.application.order;

import java.util.List;

/**
 * commerce-api의 order-events Producer가 발행하는 페이로드와 동일한 필드 계약(schema)을 갖는다.
 */
record OrderEventPayload(String eventId, String eventType, Long orderId, Long userId, List<Item> items) {

    static final String ORDER_PAID = "ORDER_PAID";

    record Item(Long productId, int quantity) {}
}
