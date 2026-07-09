package com.loopers.interfaces.consumer;

import java.time.ZonedDateTime;

/**
 * order-events 메시지 계약(JSON) 소비자 사본. 결제 성공 1건이 상품별로 분해된 라인아이템 1건을 나타낸다
 * (key=productId, quantity 만큼 판매량 누적). producer(commerce-api)의 OrderEventMessage 와 JSON 형태가 계약이다.
 */
public record OrderEventMessage(
        String eventId,
        OrderEventType type,
        Long productId,
        int quantity,
        Long orderId,
        Long userId,
        ZonedDateTime occurredAt
) {
}
