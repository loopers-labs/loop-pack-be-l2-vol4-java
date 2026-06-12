package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCriteria;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;

import java.util.List;

public class OrderV1Dto {

    // ===== 요청 =====
    public record OrderRequest(List<OrderItemRequest> items, Long couponId) {   // couponId nullable

        /** 쿠폰 미적용 주문 편의 생성자 */
        public OrderRequest(List<OrderItemRequest> items) {
            this(items, null);
        }

        /** 헤더 userId + 바디(items, couponId) 를 합쳐 응용 입력(OrderCriteria)으로 변환 */
        public OrderCriteria toCriteria(Long userId) {
            List<OrderCriteria.Line> lines = items.stream()
                    .map(i -> new OrderCriteria.Line(i.productId(), i.quantity()))
                    .toList();
            return new OrderCriteria(userId, couponId, lines);
        }
    }

    public record OrderItemRequest(Long productId, int quantity) {}

    // ===== 응답 =====
    public record OrderResponse(
            Long id,
            Long userId,
            String status,
            long totalAmount,       // 할인 전
            long discountAmount,    // 할인액
            long finalAmount,       // 최종 결제액
            Long couponId,          // 적용된 쿠폰 (없으면 null)
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.userId(),
                    info.status().name(),
                    info.totalAmount(),
                    info.discountAmount(),
                    info.finalAmount(),
                    info.userCouponId(),
                    info.items().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            int quantity,
            long unitPrice,
            String productName,
            String brandName,
            String imageUrl,
            long subtotal
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                    info.productId(), info.quantity(), info.unitPrice(),
                    info.productName(), info.brandName(), info.imageUrl(),
                    info.subtotal()
            );
        }
    }
}
