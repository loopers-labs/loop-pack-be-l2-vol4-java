package com.loopers.application.order;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final PaymentGateway paymentGateway;

    /** 쿠폰 미적용 주문. */
    public OrderInfo placeOrder(Long userId, PaymentMethod method, List<OrderLine> lines) {
        return placeOrder(userId, method, lines, null);
    }

    /**
     * 주문 실행. ① 주문 생성(PENDING)+재고차감+쿠폰사용 [tx] → ② PG 결제(최종 결제 금액) [tx 밖] → ③ 결과 반영 [tx].
     * SUCCESS→markPaid, FAILED→markFailed(+재고·쿠폰 원복), TIMEOUT→PENDING 유지 (01 §7.6, UC-17~19).
     */
    public OrderInfo placeOrder(Long userId, PaymentMethod method, List<OrderLine> lines, Long couponId) {
        OrderModel order = orderService.placeOrderPending(userId, method, lines, couponId);

        PaymentResult result = paymentGateway.pay(order.getId(), order.getFinalAmount().getAmount(), method);

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

    /**
     * PENDING 주문 정리(reconcile). pay()가 TIMEOUT으로 끝나 PENDING으로 남은 주문을
     * PG에 재조회해 최종 상태로 확정한다 (01 §7.6 보상 트랜잭션의 마지막 조각).
     * - SUCCESS → markPaid (재고·쿠폰 차감 유지)
     * - FAILED → markFailed (재고·쿠폰 원복)
     * - TIMEOUT → 아직 미확정이라 PENDING 유지 (다음 회차에 재시도)
     * 각 주문은 markPaid/markFailed의 독립 트랜잭션으로 처리되며, 한 건 실패가 배치 전체를 막지 않는다.
     * 조회~확정 사이 다른 경로로 이미 확정된 건은 CONFLICT를 잡아 건너뛴다(멱등).
     */
    public ReconcileResult reconcilePending(int page, int size) {
        List<OrderModel> pendings = orderService.getOrders(OrderStatus.PENDING, page, size);
        int paid = 0, failed = 0, stillPending = 0, skipped = 0;
        for (OrderModel order : pendings) {
            PaymentResult result = paymentGateway.inquire(order.getId());
            try {
                if (result.isSuccess()) {
                    orderService.markPaid(order.getId());
                    paid++;
                } else if (result.isFailed()) {
                    orderService.markFailed(order.getId(), result.reason());
                    failed++;
                } else {
                    stillPending++;
                }
            } catch (CoreException e) {
                // 이미 다른 경로(동시 reconcile/결제 콜백)로 PENDING을 벗어남 → 건너뜀
                skipped++;
            }
        }
        return new ReconcileResult(pendings.size(), paid, failed, stillPending, skipped);
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
