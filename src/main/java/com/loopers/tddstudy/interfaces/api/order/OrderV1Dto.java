package com.loopers.tddstudy.interfaces.api.order;

import com.loopers.tddstudy.domain.order.Order;
import java.util.List;

public class OrderV1Dto {

    // 주문 요청: 상품 목록 + (선택) 쿠폰
    public record OrderRequest(List<Item> items, Long couponId) {
        public record Item(Long productId, int quantity) {}
    }

    // 주문 응답
    public record OrderResponse(Long orderId, String status, int totalAmount) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount());
        }
    }
}
