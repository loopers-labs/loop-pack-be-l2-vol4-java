package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long id,
    Long userId,
    Long appliedUserCouponId,
    long orderTotalPrice,
    long discountAmount,
    long paymentAmount,
    List<Item> items,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {

    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getAppliedUserCouponId(),
            order.getOrderTotalPrice(),
            order.getDiscountAmount(),
            order.getPaymentAmount(),
            order.getItems().stream()
                .map(Item::from)
                .toList(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    public record Item(
        Long brandId,
        String brandName,
        Long productId,
        String productName,
        long unitPrice,
        int quantity,
        long totalPrice
    ) {

        private static Item from(OrderItem orderItem) {
            return new Item(
                orderItem.getBrandId(),
                orderItem.getBrandName(),
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getTotalPrice()
            );
        }
    }
}
