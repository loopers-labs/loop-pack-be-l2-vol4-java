package com.loopers.domain.order;

import java.util.List;

/**
 * 주문 결제 확정 도메인 이벤트. {@link OrderService#markPaid(Long)}가 결제 완료로 <b>실제 전이</b>시킬 때만
 * (이미 PAID면 markPaid가 CONFLICT로 막히므로 발행되지 않는다) 발행된다.
 *
 * <p>판매량 집계(product_metrics.sales_count)를 결제 트랜잭션에서 분리하기 위한 신호다. 도메인(OrderService)은
 * Kafka를 모르고 {@code ApplicationEventPublisher}로만 발행한다. 시스템 간 전파(Kafka)는 BEFORE_COMMIT
 * 리스너({@code OrderEventOutboxListener})가 outbox 적재로 이어받아 At Least Once로 보장한다.
 *
 * <p>품목별 {@code productId}/{@code quantity}를 그대로 실어, 소비자가 상품별 판매량을 가산할 수 있게 한다.
 *
 * @param orderId 결제 확정된 주문
 * @param items   주문 품목(상품별 수량). 소비자가 product_metrics.sales_count += quantity 로 가산.
 */
public record OrderPaidEvent(Long orderId, List<Item> items) {

    public record Item(Long productId, int quantity) {}

    public static OrderPaidEvent from(OrderModel order) {
        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getQuantity()))
                .toList();
        return new OrderPaidEvent(order.getId(), items);
    }
}
