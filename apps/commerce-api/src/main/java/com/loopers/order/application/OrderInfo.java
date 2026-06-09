package com.loopers.order.application;

import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemSnapshot;
import com.loopers.order.domain.OrderModel;

import java.util.List;

public record OrderInfo(Long id, Long memberId, Long totalAmount, List<OrderItemInfo> items) {

    public static OrderInfo from(OrderModel order) {
        List<OrderItemInfo> items = order.getItems().stream().map(OrderItemInfo::from).toList();
        return new OrderInfo(order.getId(), order.getMemberId(), order.getTotalAmount(), items);
    }

    public record OrderItemInfo(
        Long productId,
        String productName,
        String brandName,
        Long unitPrice,
        int quantity,
        Long lineAmount) {

        public static OrderItemInfo from(OrderItem item) {
            OrderItemSnapshot snapshot = item.getSnapshot();
            return new OrderItemInfo(
                snapshot.getProductId(),
                snapshot.getProductName(),
                snapshot.getBrandName(),
                snapshot.getUnitPrice(),
                item.getQuantity(),
                item.getLineAmount());
        }
    }
}
