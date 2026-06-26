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

/** 전달 수단(in-process 이벤트/Kafka)과 분리된 멱등 핸들러 — Kafka 전환 시 호출부만 바뀐다. */
@RequiredArgsConstructor
@Component
public class OrderPaymentResultHandler {

    private final OrderService orderService;
    private final StockService stockService;
    private final CouponService couponService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaid(Long orderId) {
        orderService.pay(orderId);
    }

    /** 재고·쿠폰 복원은 비멱등이므로, CREATED가 아니면(이미 CANCELED·PAID) no-op으로 중복/오보상을 막는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailed(Long orderId) {
        OrderModel order = orderService.getById(orderId);
        if (order.getStatus() != OrderStatus.CREATED) {
            return;
        }
        order.getItems().forEach(item -> stockService.increase(item.getProductId(), item.getQuantity()));
        if (order.getIssuedCouponId() != null) {
            couponService.cancel(order.getIssuedCouponId());
        }
        orderService.cancel(orderId);
    }
}
