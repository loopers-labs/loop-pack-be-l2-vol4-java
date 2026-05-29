package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCriteria;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;

import java.util.List;

public class OrderV1Dto {

    // ===== 요청 (모양 A) =====
    public record OrderRequest(List<OrderItemRequest> items) {

        /** 헤더에서 온 userId + 바디의 items 를 합쳐 응용 입력(OrderCriteria, 모양 B)으로 변환 */
        public OrderCriteria toCriteria(Long userId) {
            List<OrderCriteria.Line> lines = items.stream()
                    .map(i -> new OrderCriteria.Line(i.productId(), i.quantity()))
                    .toList();
            return new OrderCriteria(userId, lines);
        }
    }

    public record OrderItemRequest(Long productId, int quantity) {}

    // ===== 응답 =====
    public record OrderResponse(
            Long id,
            Long userId,
            String status,          // OrderStatus enum → String
            long totalAmount,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.userId(),
                    info.status().name(),   // enum 을 문자열로 풀어 응답
                    info.totalAmount(),
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
