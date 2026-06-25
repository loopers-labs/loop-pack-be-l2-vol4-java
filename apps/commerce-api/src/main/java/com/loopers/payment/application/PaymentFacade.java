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
        validatePaymentAmount(order);
        validateNoPaymentInProgress(command.orderId());

        PaymentGatewayResult result = paymentGateway.requestPayment(new PaymentGatewayPaymentCommand(
            command.userId(),
            command.orderId(),
            command.cardType(),
            command.cardNo(),
            order.getPaymentAmount()
        ));
        Payment payment = createPayment(command, order.getPaymentAmount(), result, ZonedDateTime.now());

        return PaymentInfo.from(paymentService.savePayment(payment));
    }

    private void validateOrder(Long userId, Order order) {
        if (!order.isOrderedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문은 결제할 수 없습니다.");
        }
        order.validatePayable();
    }

    private void validatePaymentAmount(Order order) {
        if (order.getPaymentAmount() <= 0) {
            throw new CoreException(ErrorType.CONFLICT, "결제 금액이 없는 주문은 PG 결제를 요청할 수 없습니다.");
        }
    }

    private void validateNoPaymentInProgress(Long orderId) {
        paymentService.findLatestPaymentByOrderId(orderId)
            .filter(Payment::isInProgress)
            .ifPresent(payment -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 결제 확인 중인 주문입니다.");
            });
    }

    private Payment createPayment(
        RequestPaymentCommand command,
        long amount,
        PaymentGatewayResult result,
        ZonedDateTime requestedAt
    ) {
        return switch (result.status()) {
            case ACCEPTED -> Payment.pending(
                command.userId(),
                command.orderId(),
                amount,
                command.cardType(),
                command.cardNo(),
                result.transaction().transactionKey(),
                requestedAt
            );
            case FAILED -> Payment.requestFailed(
                command.userId(),
                command.orderId(),
                amount,
                command.cardType(),
                command.cardNo(),
                result.failureReason(),
                requestedAt
            );
            case UNKNOWN -> Payment.unknown(
                command.userId(),
                command.orderId(),
                amount,
                command.cardType(),
                command.cardNo(),
                result.failureReason(),
                requestedAt
            );
        };
    }
}
