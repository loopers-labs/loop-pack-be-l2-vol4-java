package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;

    /** 쿠폰 미적용 주문. */
    public OrderInfo placeOrder(Long userId, PaymentMethod method, List<OrderLine> lines) {
        return placeOrder(userId, method, lines, null);
    }

    /**
     * 주문 실행. 주문 생성(PENDING)+재고차감+쿠폰사용을 한 트랜잭션으로 처리하고 PENDING 주문을 반환한다.
     * 결제는 분리돼 있어({@code POST /api/v1/payments}, {@link com.loopers.application.payment.PaymentFacade})
     * 주문 확정(markPaid/markFailed)은 PG 콜백/Reconcile 경로에서 일어난다 (03 §3.7).
     */
    public OrderInfo placeOrder(Long userId, PaymentMethod method, List<OrderLine> lines, Long couponId) {
        OrderModel order = orderService.placeOrderPending(userId, method, lines, couponId);
        return OrderInfo.from(order);
    }

    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }

    public List<OrderInfo> getMyOrders(Long userId, int page, int size) {
        return orderService.getMyOrders(userId, page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    /** 전체 주문 모니터링 (UC-12 Admin). */
    public List<OrderInfo> getOrders(OrderStatus status, int page, int size) {
        return orderService.getOrders(status, page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }
}
