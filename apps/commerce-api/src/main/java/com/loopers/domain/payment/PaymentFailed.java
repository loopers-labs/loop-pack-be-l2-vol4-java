package com.loopers.domain.payment;

public record PaymentFailed(Long orderId, String reason) {
}
