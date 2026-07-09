package com.loopers.domain.payment.event;

public record PaymentFailedEvent(Long paymentId, Long orderId, Long userId, String failureCode) {
}
