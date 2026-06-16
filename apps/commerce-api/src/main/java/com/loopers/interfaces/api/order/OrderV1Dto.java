package com.loopers.interfaces.api.order;

import com.loopers.domain.payment.PaymentMethod;
import java.util.List;

public class OrderV1Dto {

    public record CheckoutRequest(
        List<ItemRequest> items,
        Long couponIssueId,
        PaymentMethod paymentMethod
    ) {}

    public record ItemRequest(
        Long productId,
        int quantity
    ) {}

    public record CheckoutResponse(
        Long orderId
    ) {}
}
