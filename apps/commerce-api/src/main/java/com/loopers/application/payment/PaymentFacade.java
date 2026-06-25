package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    @Transactional
    public PaymentInfo requestPayment(String userLoginId, PaymentGatewayCommand command) {
        Order order = orderService.getOrder(userLoginId, command.orderId());
        return paymentService.findPayment(order.getId(), userLoginId)
            .map(PaymentInfo::from)
            .orElseGet(() -> requestNewPayment(userLoginId, order, command));
    }

    @Transactional
    public PaymentInfo syncPayment(String userLoginId, Long orderId) {
        Payment payment = paymentService.getPayment(orderId, userLoginId);
        PaymentGatewayResult result = paymentGateway.getByOrder(userLoginId, orderId);
        payment.applyGatewayResult(result);
        return PaymentInfo.from(paymentService.save(payment));
    }

    @Transactional
    public PaymentInfo handleCallback(PaymentCallbackCommand command) {
        Payment payment = paymentService.getPaymentByOrderId(command.orderId());
        payment.applyGatewayResult(command.toGatewayResult());
        return PaymentInfo.from(paymentService.save(payment));
    }

    private PaymentInfo requestNewPayment(String userLoginId, Order order, PaymentGatewayCommand command) {
        Payment payment = paymentService.save(
            new Payment(userLoginId, order.getId(), command.cardType(), command.cardNo(), order.getFinalAmount())
        );
        PaymentGatewayResult result = paymentGateway.request(
            new PaymentGatewayCommand(
                userLoginId,
                order.getId(),
                command.cardType(),
                command.cardNo(),
                order.getFinalAmount()
            )
        );
        payment.applyGatewayResult(result);
        return PaymentInfo.from(paymentService.save(payment));
    }
}
