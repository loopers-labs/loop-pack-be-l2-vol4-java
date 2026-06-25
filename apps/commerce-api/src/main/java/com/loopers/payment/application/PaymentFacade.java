package com.loopers.payment.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderService;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayPaymentCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentService;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PaymentInfo requestPayment(RequestPaymentCommand command) {
        Order order = orderService.getOrder(command.orderId());
        validateOrder(command.userId(), order);
        Payment payment = paymentService.startPayment(Payment.requesting(
            command.userId(),
            command.orderId(),
            order.getPaymentAmount(),
            command.cardType(),
            command.cardNo(),
            ZonedDateTime.now()
        ));

        PaymentGatewayResult result = paymentGateway.requestPayment(new PaymentGatewayPaymentCommand(
            command.userId(),
            command.orderId(),
            command.cardType(),
            command.cardNo(),
            order.getPaymentAmount()
        ));
        payment.applyGatewayResult(result, ZonedDateTime.now());

        return PaymentInfo.from(paymentService.savePayment(payment));
    }

    @Transactional
    public void handleCallback(PaymentCallbackCommand command) {
        Payment payment = paymentService.getPaymentByPgTransactionKey(command.transactionKey());
        payment.validateSamePayment(command.orderId(), command.amount(), command.cardType());

        if (command.isPending()) {
            return;
        }

        Order order = orderService.getOrder(payment.getOrderId());
        ZonedDateTime completedAt = ZonedDateTime.now();

        if (command.isSucceeded()) {
            payment.markSucceeded(command.transactionKey(), command.reason(), completedAt);
            order.completePayment();
            return;
        }

        payment.markFailed(command.transactionKey(), command.failureReason(), command.reason(), completedAt);
        order.failPayment();
    }

    private void validateOrder(Long userId, Order order) {
        if (!order.isOrderedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문은 결제할 수 없습니다.");
        }
        order.validatePayable();
        order.validatePaymentRequired();
    }

}
