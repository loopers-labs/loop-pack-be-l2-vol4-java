package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentPolicy;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PaymentPolicy paymentPolicy;

    @Transactional
    public PaymentInfo createPayment(Long orderId) {
        OrderModel order = orderService.get(orderId);
        paymentPolicy.validatePayable(order, ZonedDateTime.now());
        return PaymentInfo.from(paymentService.create(orderId, order.getTotalMoney()));
    }

    @Transactional
    public PaymentInfo approve(Long paymentId) {
        PaymentModel payment = paymentService.approve(paymentId);
        orderService.complete(payment.getOrderId());
        return PaymentInfo.from(payment);
    }

    @Transactional
    public PaymentInfo fail(Long paymentId) {
        return PaymentInfo.from(paymentService.fail(paymentId));
    }

    @Transactional
    public PaymentInfo expire(Long paymentId) {
        PaymentModel payment = paymentService.get(paymentId);
        OrderModel order = orderService.get(payment.getOrderId());
        paymentPolicy.validateExpirable(order, ZonedDateTime.now());
        return PaymentInfo.from(paymentService.expire(paymentId));
    }
}
