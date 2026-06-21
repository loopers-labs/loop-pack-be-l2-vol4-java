package com.loopers.domain.event.order;

import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderLine;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderPaidEvent(
    Long orderId,
    String userId,
    Long originalAmount,
    Long discountAmount,
    Long finalAmount,
    ZonedDateTime paidAt,
    List<Item> items
) {
    public static OrderPaidEvent from(Order order) {
        return new OrderPaidEvent(
            order.getId(),
            order.getUserId(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getFinalAmount(),
            ZonedDateTime.now(),
            order.getLines().stream()
                .map(Item::from)
                .toList()
        );
    }

    public record Item(
        Long productId,
        String productName,
        Integer quantity,
        Long unitPrice,
        Long lineAmount
    ) {
        private static Item from(OrderLine line) {
            return new Item(
                line.getProductId(),
                line.getProductName(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getLineAmount()
            );
        }
    }
}
