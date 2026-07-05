package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

import java.util.List;

/**
 * order-events 로 내보내는 주문 발생 사실의 payload(봉투의 data). 주문의 상품별 (productId, 수량)을 실어,
 * 소비자(streamer)가 상품별 판매량(sales_count)을 집계할 수 있게 한다. 소비자가 order 테이블을 되읽지 않도록 자기완결적.
 */
public record OrderPlacedMessage(Long orderId, List<Line> lines) {

    public record Line(Long productId, int quantity) {
    }

    public static OrderPlacedMessage of(Long orderId, List<OrderItem> items) {
        List<Line> lines = items.stream()
            .map(item -> new Line(item.getProductId(), item.getQuantity()))
            .toList();
        return new OrderPlacedMessage(orderId, lines);
    }
}
