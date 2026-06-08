package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.util.List;

/**
 * 주문 전체의 응용 계층 출력 DTO.
 * Order Aggregate 를 외부 계층(Controller)이 다루기 쉬운 형태로 평탄화한다.
 */
public record OrderInfo(
        Long id,
        Long userId,
        OrderStatus status,
        long totalAmount,
        List<OrderItemInfo> items
) {
    public static OrderInfo from(OrderModel order) {
        List<OrderItemInfo> itemInfos = order.getItems().stream()
                .map(OrderItemInfo::from)
                .toList();

        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount().amount(),
                itemInfos
        );
    }
}
