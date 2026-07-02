package com.loopers.domain.payment;

import com.loopers.domain.money.Money;

/**
 * "결제가 성공으로 확정되었다"는 이미 일어난 사실을 통지하는 이벤트.
 */
public record PaymentSucceededEvent(Long orderId, Long userId, Money amount) {
    public static PaymentSucceededEvent from(Payment payment) {
        return new PaymentSucceededEvent(payment.getOrderId(), payment.getUserId(), payment.getAmount());
    }
}
