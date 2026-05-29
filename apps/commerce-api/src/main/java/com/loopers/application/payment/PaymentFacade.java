package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;

    @Transactional
    public PaymentInfo createPayment(Long orderId) {
        OrderModel order = orderService.get(orderId);
        if (!order.isPayable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 가능한 주문 상태가 아닙니다.");
        }
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
        if (!order.isExpirable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 만료 가능 시간(15분)이 지나지 않았습니다.");
        }
        return PaymentInfo.from(paymentService.expire(paymentId));
    }
}
