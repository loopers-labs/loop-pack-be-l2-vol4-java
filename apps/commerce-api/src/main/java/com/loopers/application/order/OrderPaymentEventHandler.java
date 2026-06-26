package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.event.PaymentFailedEvent;
import com.loopers.domain.payment.event.PaymentSucceededEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 결과 → 주문 상태 전이 + 재고 복구.
 *
 *  - AFTER_COMMIT 으로 PaymentService 의 상태 전이가 정상 commit 된 뒤에만 트리거.
 *  - REQUIRES_NEW 로 별도 트랜잭션 — 콜백/폴링 핸들러 흐름과 격리.
 *
 * 멱등성:
 *  - PAID 전이: Order.markPaid() 자체 멱등.
 *  - FAILED 전이 + 재고 복구: Order.status == FAILED 이면 통째로 스킵해 중복 재고 복구 차단.
 *    (Order.markFailed() 의 멱등성만 의존하면 markFailed 는 무시되지만 재고는 두 번 +되는 위험)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderPaymentEventHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        orderRepository.find(event.orderId()).ifPresentOrElse(
            Order::markPaid,
            () -> log.error("[OrderEventHandler] Order 없음 (성공 이벤트). orderId={}", event.orderId())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        // RECOVERABLE 실패 — 사용자가 다른 카드로 재시도 가능한 케이스.
        // Order 와 재고를 유지해 같은 주문으로 즉시 새 결제가 가능하게 한다.
        if (event.category().isRecoverable()) {
            log.info("[OrderEventHandler] Recoverable 실패 — Order/재고 유지 (재시도 가능). orderId={}, reason={}",
                event.orderId(), event.reason());
            return;
        }

        Order order = orderRepository.find(event.orderId()).orElse(null);
        if (order == null) {
            log.error("[OrderEventHandler] Order 없음 (실패 이벤트). orderId={}", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.FAILED) {
            log.debug("[OrderEventHandler] 이미 FAILED — 재고 복구 스킵 (멱등). orderId={}", event.orderId());
            return;
        }
        if (order.getStatus() == OrderStatus.PAID) {
            log.warn("[OrderEventHandler] PAID 상태에서 FAILED 이벤트 수신 — 위험 케이스, 운영 확인 필요. orderId={}",
                event.orderId());
            return;
        }

        order.markFailed();
        restoreStocks(order);
    }

    private void restoreStocks(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.find(item.getProductId()).orElse(null);
            if (product == null) {
                log.error("[OrderEventHandler] 재고 복구 대상 상품 없음. productId={}", item.getProductId());
                continue;
            }
            product.restoreStock(item.getQuantity().value());
        }
    }
}
