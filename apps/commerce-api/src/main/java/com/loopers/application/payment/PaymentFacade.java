package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentCreationResult;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final PaymentStatusSynchronizer paymentStatusSynchronizer;

    @Transactional
    public PaymentInfo requestPayment(String userLoginId, PaymentGatewayCommand command) {
        Order order = orderService.getOrder(userLoginId, command.orderId());
        return paymentService.findPayment(order.getId(), userLoginId)
            .map(PaymentInfo::from)
            .orElseGet(() -> requestNewPayment(userLoginId, order, command));
    }

    @Transactional
    public PaymentInfo syncPayment(String userLoginId, Long orderId) {
        return paymentStatusSynchronizer.syncPayment(userLoginId, orderId);
    }

    @Transactional
    public PaymentInfo handleCallback(PaymentCallbackCommand command) {
        Payment payment = paymentService.getPaymentByOrderId(command.orderId());
        payment.applyGatewayResult(command.toGatewayResult());
        return PaymentInfo.from(saveGatewayResult(payment));
    }

    private PaymentInfo requestNewPayment(String userLoginId, Order order, PaymentGatewayCommand command) {
        if (!paymentGateway.isRequestAvailable()) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "PG 결제 요청이 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.");
        }
        PaymentCreationResult creationResult = paymentService.create(
            new Payment(userLoginId, order.getId(), command.cardType(), command.cardNo(), order.getFinalAmount())
        );
        Payment payment = creationResult.payment();
        if (!creationResult.created()) {
            return PaymentInfo.from(payment);
        }
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
        return PaymentInfo.from(saveGatewayResult(payment));
    }

    private Payment saveGatewayResult(Payment payment) {
        return paymentService.completeIfPending(payment);
    }
}
