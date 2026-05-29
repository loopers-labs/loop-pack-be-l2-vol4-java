package com.loopers.domain.order;

import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Order + Stock 크로스 애그리거트 도메인 서비스.
 *
 * 주문 상태 전이 시 재고 조작이 항상 함께 발생하는 비즈니스 규칙을 담는다.
 * - OrderModel 단독에 넣으면 StockService에 의존해야 함 (불가)
 * - StockService 단독에 넣으면 OrderModel을 알아야 함 (불가)
 * → 어느 쪽에도 속하지 않는 크로스 애그리거트 규칙 → Domain Service
 */
@RequiredArgsConstructor
@Component
public class OrderStockService {

    private final OrderService orderService;
    private final StockService stockService;

    /** 결제 확정 — 아이템별 재고 확정 + 주문 확정 (멱등: 이미 CONFIRMED면 스킵) */
    public void confirmOrder(OrderModel order, Long paymentAmount) {
        if (!order.isPending()) return;
        order.getItems().forEach(item -> stockService.confirm(item.getProductId(), item.getQuantity()));
        orderService.confirm(order, paymentAmount);
    }

    /** 주문 취소 — 아이템별 재고 복구 + 주문 취소 (CONFIRMED 상태만) */
    public void cancelOrder(OrderModel order) {
        order.getItems().forEach(item -> stockService.restore(item.getProductId(), item.getQuantity()));
        orderService.cancel(order);
    }

    /** 주문 만료 — 아이템별 재고 해제 + 주문 실패 (스케줄러용) */
    public void expireOrder(OrderModel order) {
        order.getItems().forEach(item -> stockService.release(item.getProductId(), item.getQuantity()));
        orderService.fail(order);
    }

    /** 결제 실패 — PENDING이면 아이템별 재고 해제 + 주문 실패 (멱등) */
    public void failOrder(OrderModel order) {
        if (order.isPending()) {
            order.getItems().forEach(item -> stockService.release(item.getProductId(), item.getQuantity()));
            orderService.fail(order);
        }
    }
}
