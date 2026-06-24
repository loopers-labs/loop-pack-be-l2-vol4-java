package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 결과를 주문에 반영하는 transport-agnostic 핸들러.
 * in-process 이벤트든 (다음 주) Kafka 컨슈머든 이 메서드를 호출만 하면 되도록, 전달 수단과 분리하고 멱등하게 설계한다.
 */
@RequiredArgsConstructor
@Component
public class OrderPaymentResultHandler {

    private final OrderService orderService;
    private final StockService stockService;
    private final CouponService couponService;

    /** 결제 성공 → 주문 PAID. 이미 PAID면 도메인 멱등 가드가 흡수한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaid(Long orderId) {
        orderService.pay(orderId);
    }

    /**
     * 결제 실패 → 보상(재고·쿠폰 복원) 후 주문 CANCELED.
     * 이미 CANCELED면 전체 no-op — 재고·쿠폰 복원은 비멱등이므로, 중복 이벤트에 재복원하지 않도록 상태로 가드한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailed(Long orderId) {
        OrderModel order = orderService.getById(orderId);
        if (order.getStatus() == OrderStatus.CANCELED) {
            return;
        }
        order.getItems().forEach(item -> stockService.increase(item.getProductId(), item.getQuantity()));
        if (order.getIssuedCouponId() != null) {
            couponService.cancel(order.getIssuedCouponId());
        }
        orderService.cancel(orderId);
    }
}
