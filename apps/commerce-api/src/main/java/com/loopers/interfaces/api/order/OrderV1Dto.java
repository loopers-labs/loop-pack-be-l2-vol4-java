package com.loopers.interfaces.api.order;

import com.loopers.application.order.CreateOrderCommand;
import com.loopers.application.order.OrderInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
        @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.")
        List<@NotNull(message = "주문 항목은 비어있을 수 없습니다.") @Valid Item> items
    ) {

        public CreateOrderCommand toCommand(Long userId) {
            List<CreateOrderCommand.Item> commandItems = items == null
                ? List.of()
                : items.stream()
                    .map(item -> new CreateOrderCommand.Item(item.productId(), item.quantity()))
                    .toList();
            return new CreateOrderCommand(userId, commandItems);
        }

        public record Item(
            @NotNull(message = "상품 ID는 비어있을 수 없습니다.")
            Long productId,

            @NotNull(message = "주문 수량은 비어있을 수 없습니다.")
            @Positive(message = "주문 수량은 1 이상이어야 합니다.")
            Integer quantity
        ) {
        }
    }

    public record OrderResponse(
        Long id,
        Long userId,
        long orderTotalPrice,
        List<Item> items
    ) {

        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                info.id(),
                info.userId(),
                info.orderTotalPrice(),
                info.items().stream()
                    .map(Item::from)
                    .toList()
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
