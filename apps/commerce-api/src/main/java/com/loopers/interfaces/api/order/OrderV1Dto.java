package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record PlaceOrderRequest(List<Item> items, Long couponId) {
        public PlaceOrderRequest(List<Item> items) {
            this(items, null);
        }

        public record Item(Long productId, Integer quantity) {
        }

        public OrderCommand.Place toCommand() {
            List<OrderCommand.Line> lines = items == null ? List.of()
                : items.stream()
                    .map(item -> new OrderCommand.Line(item.productId(), item.quantity()))
                    .toList();
            return new OrderCommand.Place(lines, couponId);
        }
    }

    public record OrderResponse(
        Long id,
        Long userId,
        Long totalAmount,
        Long discountAmount,
        Long finalAmount,
        Long usedCouponId,
        OrderStatus status,
        List<Item> items,
        ZonedDateTime createdAt
    ) {
        public record Item(
            Long productId,
            Integer quantity,
            String productName,
            Long productPrice,
            String brandName
        ) {
            public static Item from(OrderInfo.Item info) {
                return new Item(
                    info.productId(),
                    info.quantity(),
                    info.productName(),
                    info.productPrice(),
                    info.brandName()
                );
            }
        }

        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.totalAmount(),
                info.discountAmount(),
                info.finalAmount(),
                info.usedCouponId(),
                info.status(),
                info.items().stream().map(Item::from).toList(),
                info.createdAt()
            );
        }
    }

    public record MyOrderSummary(
        Long id,
        Long totalAmount,
        Long discountAmount,
        Long finalAmount,
        OrderStatus status,
        ZonedDateTime createdAt
    ) {
        public static MyOrderSummary from(OrderInfo info) {
            return new MyOrderSummary(
                info.id(),
                info.totalAmount(),
                info.discountAmount(),
                info.finalAmount(),
                info.status(),
                info.createdAt()
            );
        }
    }

    public record MyOrderDetail(
        Long id,
        Long totalAmount,
        Long discountAmount,
        Long finalAmount,
        Long usedCouponId,
        OrderStatus status,
        List<OrderResponse.Item> items,
        ZonedDateTime createdAt
    ) {
        public static MyOrderDetail from(OrderInfo info) {
            return new MyOrderDetail(
                info.id(),
                info.totalAmount(),
                info.discountAmount(),
                info.finalAmount(),
                info.usedCouponId(),
                info.status(),
                info.items().stream().map(OrderResponse.Item::from).toList(),
                info.createdAt()
            );
        }
    }
}
