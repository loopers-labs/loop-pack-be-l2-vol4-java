package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final PaymentGateway paymentGateway;

    /**
     * 주문 실행. ① 주문 생성(PENDING)+재고차감 [tx] → ② PG 결제 [tx 밖] → ③ 결과 반영 [tx].
     * SUCCESS→markPaid, FAILED→markFailed(+재고원복), TIMEOUT→PENDING 유지 (01 §7.6, UC-08).
     */
    public OrderInfo placeOrder(Long userId, PaymentMethod method, List<OrderLine> lines) {
        OrderModel order = orderService.placeOrderPending(userId, method, lines);

        PaymentResult result = paymentGateway.pay(order.getId(), order.getTotalAmount().getAmount(), method);

        OrderModel finalized;
        if (result.isSuccess()) {
            finalized = orderService.markPaid(order.getId());
        } else if (result.isFailed()) {
            finalized = orderService.markFailed(order.getId(), result.reason());
        } else {
            // TIMEOUT: 주문은 PENDING 유지 — 재확인(reconcile)은 별도 처리
            finalized = order;
        }
        return OrderInfo.from(finalized);
    }

    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    public List<OrderInfo> getMyOrders(Long userId, int page, int size) {
        return orderService.getMyOrders(userId, page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }
}
