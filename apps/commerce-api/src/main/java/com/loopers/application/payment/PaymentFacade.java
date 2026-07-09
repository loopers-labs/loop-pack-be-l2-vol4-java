package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.interfaces.event.payment.PaymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentModel requestPayment(Long memberId, Long orderId, CardType cardType, String cardNo, Long amount) {
        PaymentModel payment = paymentService.create(orderId, cardType, cardNo, amount);
        eventPublisher.publishEvent(PaymentRequestedEvent.of(payment, memberId));
        return payment;
    }

    @Transactional
    public void recoverAsSuccess(Long orderId, String transactionKey) {
        paymentService.successByOrderId(orderId, transactionKey);
        orderService.confirmBySystem(orderId);
    }

    public void recoverAsFailure(Long orderId, String reason) {
        paymentService.failByOrderId(orderId, reason);
        OrderModel order = orderService.getById(orderId);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            orderService.cancelBySystem(orderId);
        }
    }

    @Transactional
    public void handleCallback(String transactionKey, String status, String reason) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);

        if ("SUCCESS".equals(status)) {
            if (payment.getStatus() != PaymentStatus.PENDING) return;
            paymentService.success(transactionKey);
            orderService.confirmBySystem(payment.getOrderId());
        } else if ("FAILED".equals(status)) {
            if (payment.getStatus() != PaymentStatus.PENDING) return;
            paymentService.failByTransactionKey(transactionKey, reason);
            OrderModel order = orderService.getById(payment.getOrderId());
            if (order.getStatus() != OrderStatus.CANCELLED) {
                orderService.cancelBySystem(payment.getOrderId());
            }
        } else {
            log.warn("[transactionKey={}] 알 수 없는 콜백 상태 수신: {}", transactionKey, status);
        }
    }
}
