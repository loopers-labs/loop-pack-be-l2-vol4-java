package com.loopers.application.order;

import com.loopers.domain.payment.PaymentMethod;
import java.util.List;

public record OrderCheckoutRequest(
    List<Item> items,
    Long couponIssueId,
    PaymentMethod paymentMethod
) {
    public record Item(
        Long productId,
        int quantity
    ) {}
}
