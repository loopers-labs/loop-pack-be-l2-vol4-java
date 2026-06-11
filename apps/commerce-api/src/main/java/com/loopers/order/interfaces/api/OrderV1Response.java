package com.loopers.order.interfaces.api;

import com.loopers.order.application.OrderResult;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Response {

    public record Detail(
        Long orderId,
        String orderNumber,
        String status,
        long totalAmount,
        ZonedDateTime orderedAt,
        Recipient recipient,
        List<Item> items
    ) {
        public static Detail from(OrderResult.Detail result) {
            return new Detail(
                result.orderId(),
                result.orderNumber(),
                result.status().name(),
                result.totalAmount(),
                result.orderedAt(),
                Recipient.from(result.recipient()),
                result.items().stream().map(Item::from).toList()
            );
        }
    }

    public record Summary(
        Long orderId,
        Long userId,
        String orderNumber,
        String status,
        long totalAmount,
        ZonedDateTime orderedAt
    ) {
        public static Summary from(OrderResult.Summary result) {
            return new Summary(
                result.orderId(),
                result.userId(),
                result.orderNumber(),
                result.status().name(),
                result.totalAmount(),
                result.orderedAt()
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
        public static Item from(OrderResult.Item result) {
            return new Item(
                result.productId(),
                result.productName(),
                result.brandId(),
                result.brandName(),
                result.price(),
                result.quantity(),
                result.subtotal()
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
        public static Recipient from(OrderResult.Recipient result) {
            return new Recipient(
                result.recipientName(),
                result.recipientPhone(),
                result.zipcode(),
                result.address1(),
                result.address2()
            );
        }
    }
}
