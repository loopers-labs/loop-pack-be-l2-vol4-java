package com.loopers.interfaces.api.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public class OrderAdminDto {
    public record OrderResponse(
            Long orderId,
            Long userId,
            OrderStatus status,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderModel order) {
            return new OrderResponse(
                    order.getId(),
                    order.getUserId(),
                    order.getStatus(),
                    order.getItems().stream()
                            .map(item -> new OrderItemResponse(
                                    item.getProductId(),
                                    item.getSnapshot().getName(),
                                    item.getSnapshot().getPrice(),
                                    item.getSnapshot().getBrandName(),
                                    item.getQuantity()
                            )).toList()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            String name,
            BigDecimal price,
            String brandName,
            int quantity
    ) {}
}
