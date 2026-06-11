package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class OrderDto {

    public record CreateRequest(
        @NotEmpty(message = "주문 항목은 필수입니다.")
        List<OrderItemRequest> items
    ) {}

    public record OrderItemRequest(Long productId, int quantity) {}

    public record OrderResponse(Long orderId, String status, Long totalPrice, List<OrderItemResponse> items) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.orderId(),
                info.status(),
                info.totalPrice(),
                info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderItemResponse(Long productId, String productName, Long price, int quantity) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
            return new OrderItemResponse(info.productId(), info.productName(), info.price(), info.quantity());
        }
    }

    public static List<OrderItemCommand> toCommands(List<OrderItemRequest> requests) {
        return requests.stream()
            .map(r -> new OrderItemCommand(r.productId(), r.quantity()))
            .toList();
    }
}
