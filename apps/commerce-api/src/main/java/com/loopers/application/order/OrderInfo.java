package com.loopers.application.order;

import com.loopers.domain.order.Order;

public class OrderInfo {

    public record Create(Long orderId) {
        public static Create from(Order order) {
            return new Create(order.getId());
        }
    }
}
