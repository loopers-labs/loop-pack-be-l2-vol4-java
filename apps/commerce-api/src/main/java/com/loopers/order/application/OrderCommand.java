package com.loopers.order.application;

import java.util.List;

public class OrderCommand {

    public record Create(
        Long userId,
        List<Line> items,
        String recipientName,
        String recipientPhone,
        String zipcode,
        String address1,
        String address2,
        Long userCouponId
    ) {
    }

    public record Line(
        Long productId,
        int quantity
    ) {
    }
}
