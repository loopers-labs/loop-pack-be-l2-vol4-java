package com.loopers.interfaces.apiadmin.order;

import com.loopers.application.order.OrderInfo;
import org.springframework.data.domain.Page;

import java.util.List;

public class OrderAdminV1Dto {

    public record OrderResponse(
            Long id,
            String orderNumber,
            Long userId,
            Long totalAmount,
            String status,
            List<OrderItemResponse> items
    ) {
        public record OrderItemResponse(
                Long id,
                Long productId,
                String productName,
                Long productPrice,
                Integer quantity
        ) {
            public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
                return new OrderItemResponse(
                        item.id(), item.productId(), item.productName(),
                        item.productPrice(), item.quantity()
                );
            }
        }

        public static Page<OrderResponse> from(Page<OrderInfo> page) {
            return page.map(OrderResponse::from);
        }

        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.orderNumber(),
                    info.userId(),
                    info.totalAmount(),
                    info.status(),
                    info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }
}