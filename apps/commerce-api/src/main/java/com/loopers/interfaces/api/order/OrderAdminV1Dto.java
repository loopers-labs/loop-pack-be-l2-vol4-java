package com.loopers.interfaces.api.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    public record OrderListResponse(
            Long id,
            Long userId,
            String status,
            BigDecimal totalPrice,
            ZonedDateTime createdAt
    ) {
        public static OrderListResponse from(OrderModel order) {
            return new OrderListResponse(
                    order.getId(),
                    order.getUserId(),
                    order.getStatus().name(),
                    order.getTotalPrice(),
                    order.getCreatedAt()
            );
        }
    }

    public record OrderDetailResponse(
            Long id,
            Long userId,
            String status,
            BigDecimal totalPrice,
            List<OrderItemResponse> items,
            ZonedDateTime createdAt
    ) {
        public static OrderDetailResponse from(OrderModel order) {
            return new OrderDetailResponse(
                    order.getId(),
                    order.getUserId(),
                    order.getStatus().name(),
                    order.getTotalPrice(),
                    order.getItems().stream().map(OrderItemResponse::from).toList(),
                    order.getCreatedAt()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            BigDecimal productPrice,
            Long quantity,
            BigDecimal subtotal
    ) {
        public static OrderItemResponse from(OrderItemModel item) {
            return new OrderItemResponse(
                    item.getProductId(),
                    item.getProductSnapshot().name(),
                    item.getProductSnapshot().price(),
                    item.getQuantity(),
                    item.subtotal()
            );
        }
    }
}
