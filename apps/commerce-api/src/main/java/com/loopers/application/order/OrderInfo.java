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
        long totalAmount,       // 할인 전
        long discountAmount,    // 할인액
        long finalAmount,       // 최종 결제액
        Long userCouponId,      // 적용된 쿠폰 (없으면 null)
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
                order.getDiscountAmount().amount(),
                order.getFinalAmount().amount(),
                order.getUserCouponId().orElse(null),
                itemInfos
        );
    }
}
