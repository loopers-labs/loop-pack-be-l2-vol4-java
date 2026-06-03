package com.loopers.interfaces.api.ordering;

import com.loopers.application.ordering.order.OrderCommand;
import com.loopers.application.ordering.order.OrderResult;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.domain.payment.payment.PaymentStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderDto {
    public record OrderCreateRequest(List<OrderCreateItemRequest> items) {
        public OrderCommand.Create toCommand(String userId) {
            return new OrderCommand.Create(
                userId,
                items == null
                    ? List.of()
                    : items.stream()
                        .map(item -> new OrderCommand.Item(item.productId(), item.quantity()))
                        .toList()
            );
        }
    }

    public record OrderCreateItemRequest(Long productId, Integer quantity) {}

    public record OrderCreateResponse(
        Long orderId,
        OrderStatus orderStatus,
        Long totalAmount
    ) {
        public static OrderCreateResponse from(OrderResult.Detail result) {
            return new OrderCreateResponse(
                result.orderId(),
                result.orderStatus(),
                result.totalAmount()
            );
        }
    }

    public record OrderListItemResponse(
        Long orderId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        Long totalAmount,
        ZonedDateTime createdAt
    ) {
        public static OrderListItemResponse from(OrderResult.Summary result) {
            return new OrderListItemResponse(
                result.orderId(),
                result.orderStatus(),
                result.paymentStatus(),
                result.totalAmount(),
                result.createdAt()
            );
        }
    }

    public record OrderDetailResponse(
        Long orderId,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        String failureReason,
        Long totalAmount,
        List<OrderDetailItemResponse> items
    ) {
        public static OrderDetailResponse from(OrderResult.Detail result) {
            return new OrderDetailResponse(
                result.orderId(),
                result.orderStatus(),
                result.paymentStatus(),
                result.failureReason(),
                result.totalAmount(),
                result.items().stream()
                    .map(OrderDetailItemResponse::from)
                    .toList()
            );
        }
    }

    public record OrderDetailItemResponse(
        Long productId,
        String productName,
        Integer quantity,
        Long unitPrice,
        Long lineAmount
    ) {
        private static OrderDetailItemResponse from(OrderResult.Item item) {
            return new OrderDetailItemResponse(
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice(),
                item.lineAmount()
            );
        }
    }
}
