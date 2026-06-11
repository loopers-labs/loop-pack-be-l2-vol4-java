package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
            List<Item> items
    ) {
        public OrderCommand toCommand() {
            List<OrderCommand.Item> commandItems = items == null 
                                                   ? List.of() 
                                                   : items.stream()
                                                          .map(
                                                            i -> new OrderCommand.Item(i.productId(), i.quantity())
                                                          )
                                                          .toList();
            return new OrderCommand(commandItems);
        }
    }

    public record Item(
            Long productId, 
            Integer quantity
    ) { }

    public record OrderResponse(
            Long id,
            Long userId,
            OrderStatus status,
            Long totalAmount,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.userId(),
                    info.status(),
                    info.totalAmount(),
                    info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            Long unitPrice,
            Integer quantity,
            Long subtotal
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                    info.productId(),
                    info.productName(),
                    info.unitPrice(),
                    info.quantity(),
                    info.subtotal()
            );
        }
    }
}