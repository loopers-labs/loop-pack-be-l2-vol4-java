package com.loopers.order.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderStatus;
import com.loopers.order.domain.ShippingDestination;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderResult {

    /**
     * 단건 상세. 주문 생성 결과로 주문 항목 스냅샷을 함께 담는다.
     */
    public record Detail(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        long totalAmount,
        ZonedDateTime orderedAt,
        Recipient recipient,
        List<Item> items
    ) {
        public static Detail of(Order order, List<OrderItem> items) {
            return new Detail(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt(),
                Recipient.from(order.getShippingDestination()),
                items.stream().map(Item::from).toList()
            );
        }
    }

    /**
     * 목록 요약. 주문 단위 정보만 담고 항목은 포함하지 않는다.
     */
    public record Summary(
        Long orderId,
        Long userId,
        String orderNumber,
        OrderStatus status,
        long totalAmount,
        ZonedDateTime orderedAt
    ) {
        public static Summary from(Order order) {
            return new Summary(
                order.getId(),
                order.getUserId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderedAt()
            );
        }
    }

    public record Item(
        Long productId,
        String productName,
        Long brandId,
        String brandName,
        long price,
        int quantity,
        long subtotal
    ) {
        public static Item from(OrderItem item) {
            return new Item(
                item.getProductId(),
                item.getProductName(),
                item.getBrandId(),
                item.getBrandName(),
                item.getPrice(),
                item.getQuantity(),
                item.subtotal()
            );
        }
    }

    public record Recipient(
        String recipientName,
        String recipientPhone,
        String zipcode,
        String address1,
        String address2
    ) {
        public static Recipient from(ShippingDestination destination) {
            return new Recipient(
                destination.getRecipientName(),
                destination.getRecipientPhone(),
                destination.getZipcode(),
                destination.getAddress1(),
                destination.getAddress2()
            );
        }
    }
}
