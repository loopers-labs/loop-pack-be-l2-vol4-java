package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentInitiator {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @Transactional
    public Initiated initiate(Long userId, Long orderId) {
        OrderModel order = orderService.startPayment(userId, orderId);
        PaymentModel payment = paymentService.createPending(userId, orderId, order.getFinalAmount());
        return new Initiated(payment.getId(), order.getFinalAmount());
    }

    public record Initiated(Long paymentId, Long amount) {
    }
}
