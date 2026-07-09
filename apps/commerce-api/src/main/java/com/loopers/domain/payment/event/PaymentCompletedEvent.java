package com.loopers.domain.payment.event;

import java.util.List;

public record PaymentCompletedEvent(Long paymentId, Long orderId, Long userId, List<Item> items) {

    public record Item(Long productId, int quantity) {}
}
