package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetail;
import com.loopers.application.order.OrderSummary;
import com.loopers.domain.order.model.OrderItemStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(List<OrderItemDto> items, Long couponId) {
        public record OrderItemDto(Long productId, int quantity) {}
    }

    public record CreateResponse(Long orderId) {}

    public record OrderSummaryResponse(
        Long orderId,
        Long originalAmount,
        Long discountAmount,
        Long totalAmount,
        ZonedDateTime orderedAt
    ) {
        public static OrderSummaryResponse from(OrderSummary summary) {
            return new OrderSummaryResponse(
                summary.orderId(),
                summary.originalAmount(),
                summary.discountAmount(),
                summary.totalAmount(),
                summary.orderedAt()
            );
        }
    }

    public record OrderDetailResponse(
        Long orderId,
        Long originalAmount,
        Long discountAmount,
        Long totalAmount,
        ZonedDateTime orderedAt,
        List<OrderItemResponse> items
    ) {
        public record OrderItemResponse(
            String productName,
            String brandName,
            Long price,
            int quantity,
            OrderItemStatus status
        ) {}

        public static OrderDetailResponse from(OrderDetail detail) {
            List<OrderItemResponse> items = detail.items().stream()
                .map(item -> new OrderItemResponse(
                    item.productName(), item.brandName(), item.price(), item.quantity(), item.status()
                ))
                .toList();
            return new OrderDetailResponse(
                detail.orderId(),
                detail.originalAmount(),
                detail.discountAmount(),
                detail.totalAmount(),
                detail.orderedAt(),
                items
            );
        }
    }
}
