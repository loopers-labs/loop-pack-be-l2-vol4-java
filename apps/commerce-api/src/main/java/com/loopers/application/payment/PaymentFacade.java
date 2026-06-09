package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStockService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Component
@Transactional
public class PaymentFacade {

    private final OrderService orderService;
    private final OrderStockService orderStockService;
    private final PaymentService paymentService;

    /** 결제 확정 — 재고+주문 확정(금액/상태 검증 포함) + 결제 저장 (멱등) */
    public PaymentInfo confirm(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.get(orderId);
        orderStockService.confirmOrder(order, amount);
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.SUCCESS, amount)
        );
        return PaymentInfo.from(payment);
    }

    /** 결제 실패 — PENDING이면 재고 해제 + 주문 실패 (멱등) + 결제 저장 (멱등) */
    public PaymentInfo fail(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.get(orderId);
        orderStockService.failOrder(order);
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.FAILED, amount)
        );
        return PaymentInfo.from(payment);
    }
}
