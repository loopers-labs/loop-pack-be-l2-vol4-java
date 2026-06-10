package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.util.List;

public record OrderInfo(
        Long id,
        String orderNumber,
        Long userId,
        Long originalAmount,
        Long discountAmount,
        Long totalAmount,
        String status,
        List<OrderItemInfo> items
) {
    public record OrderItemInfo(
            Long id,
            Long productId,
            String productName,
            Long productPrice,
            Integer quantity
    ) {
        public static OrderItemInfo from(OrderItemModel item) {
            return new OrderItemInfo(
                    item.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getProductPrice().getValue(),
                    item.getQuantity().getValue()
            );
        }
    }

    public static OrderInfo from(OrderModel order) {
        return new OrderInfo(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getOriginalAmount().getValue(),
                order.getDiscountAmount().getValue(),
                order.getTotalMoney().getValue(),
                order.getStatus().getDescription(),
                order.getItems().stream().map(OrderItemInfo::from).toList()
        );
    }
}
