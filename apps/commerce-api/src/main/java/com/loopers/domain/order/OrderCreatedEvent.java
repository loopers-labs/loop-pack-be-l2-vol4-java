package com.loopers.domain.order;

import com.loopers.domain.money.Money;

/**
 * "주문이 생성되었다"는 이미 일어난 사실을 통지하는 이벤트.
 * 주문 생성(핵심)과 무관한 후속(행동 로깅 등)은 이 이벤트를 구독해 반응한다.
 */
public record OrderCreatedEvent(Long orderId, Long userId, Money paymentAmount) {
    public static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(order.getId(), order.getUserId(), order.getPaymentAmount());
    }
}
