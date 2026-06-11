package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    public record OrderResponse(
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

        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.appliedUserCouponId(),
                info.orderTotalPrice(),
                info.discountAmount(),
                info.paymentAmount(),
                info.items().stream()
                    .map(Item::from)
                    .toList(),
                info.createdAt(),
                info.updatedAt()
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

            private static Item from(OrderInfo.Item item) {
                return new Item(
                    item.brandId(),
                    item.brandName(),
                    item.productId(),
                    item.productName(),
                    item.unitPrice(),
                    item.quantity(),
                    item.totalPrice()
                );
            }
        }
    }
}
