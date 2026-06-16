package com.loopers.interfaces.api.order;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderItemInfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class OrderV1Dto {

    public record CreateRequest(
        @NotEmpty(message = "주문 항목은 비어 있을 수 없습니다.")
        @Valid
        List<OrderItemRequest> items,

        Long userCouponId
    ) {

        public List<OrderItemCommand> toCommandItems() {
            return items.stream()
                .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
                .toList();
        }
    }

    public record OrderItemRequest(
        @NotNull(message = "상품 식별자는 null일 수 없습니다.")
        Long productId,

        @NotNull(message = "주문 수량은 null일 수 없습니다.")
        @Positive(message = "주문 수량은 1 이상이어야 합니다.")
        Integer quantity
    ) {
    }

    public record OrderResponse(
        Long orderId,
        String status,
        ZonedDateTime orderedAt,
        Integer originalAmount,
        Integer discountAmount,
        Integer finalAmount,
        Long userCouponId,
        List<OrderItemResponse> items
    ) {

        public static OrderResponse from(OrderInfo orderInfo) {
            return new OrderResponse(
                orderInfo.orderId(),
                orderInfo.status().name(),
                orderInfo.orderedAt(),
                orderInfo.originalAmount(),
                orderInfo.discountAmount(),
                orderInfo.finalAmount(),
                orderInfo.userCouponId(),
                orderInfo.items()
                    .stream()
                    .map(OrderItemResponse::from)
                    .toList()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        String brandName,
        Integer unitPrice,
        Integer quantity
    ) {

        public static OrderItemResponse from(OrderItemInfo orderItemInfo) {
            return new OrderItemResponse(
                orderItemInfo.productId(),
                orderItemInfo.productName(),
                orderItemInfo.brandName(),
                orderItemInfo.unitPrice(),
                orderItemInfo.quantity()
            );
        }
    }

    public record PageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static PageResponse from(Page<OrderInfo> ordersInfo) {
            List<OrderResponse> content = ordersInfo.getContent()
                .stream()
                .map(OrderResponse::from)
                .toList();

            return new PageResponse(
                content,
                ordersInfo.getNumber(),
                ordersInfo.getSize(),
                ordersInfo.getTotalElements(),
                ordersInfo.getTotalPages()
            );
        }
    }
}
