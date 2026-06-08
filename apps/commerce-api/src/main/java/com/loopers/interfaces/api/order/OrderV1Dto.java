package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderItemInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderV1Dto {

    public record OrderItemRequest(
            @NotNull(message = "재고 ID는 필수입니다.") Long stockId,
            @Min(value = 1, message = "수량은 1 이상이어야 합니다.") int quantity
    ) {}

    public record OrderRequest(
            @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.") @Valid List<OrderItemRequest> items
    ) {
        public List<OrderItemInput> toInputs() {
            return items.stream()
                    .map(req -> new OrderItemInput(req.stockId(), req.quantity()))
                    .toList();
        }
    }

    public record OrderResponse(
            Long id,
            String orderNumber,
            Long totalAmount,
            String status,
            List<OrderItemResponse> items
    ) {
        public record OrderItemResponse(
                Long id,
                Long productId,
                String productName,
                Long productPrice,
                Integer quantity
        ) {
            public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
                return new OrderItemResponse(
                        item.id(), item.productId(), item.productName(),
                        item.productPrice(), item.quantity()
                );
            }
        }

        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.orderNumber(),
                    info.totalAmount(),
                    info.status(),
                    info.items().stream().map(OrderItemResponse::from).toList()
            );
        }

        public static List<OrderResponse> from(List<OrderInfo> infos) {
            return infos.stream().map(OrderResponse::from).toList();
        }
    }
}
