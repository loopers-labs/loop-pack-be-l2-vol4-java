package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 합성. PG 호출이 끼므로 Facade에 트랜잭션을 두지 않는다 — 각 Service 호출이 자기 트랜잭션이고,
 * PG 호출은 어떤 트랜잭션에도 들어가지 않는다.
 */
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PaymentInfo requestPayment(Long userId, Long orderId, CardType cardType, String cardNo) {
        OrderModel order = orderService.getById(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        long amount = order.getFinalAmount().value();

        paymentService.createPending(orderId, userId, cardType, Money.of(amount));

        GatewayResult result = paymentGateway.requestPayment(
            new GatewayCommand(orderId, userId, cardType, cardNo, amount));
        if (result.accepted()) {
            paymentService.assignTransactionKey(orderId, result.transactionKey());
        }

        return PaymentInfo.from(paymentService.getByOrderId(orderId));
    }

    /** PG 콜백 수신. PG 상태 문자열을 우리 확정으로 변환한다. PENDING 등 미확정 상태는 무시한다. */
    public void handleCallback(String transactionKey, String pgStatus, String reason) {
        if ("SUCCESS".equals(pgStatus)) {
            paymentService.confirm(transactionKey, true, null);
        } else if ("FAILED".equals(pgStatus)) {
            paymentService.confirm(transactionKey, false, reason);
        }
    }

    public PaymentInfo getStatus(Long userId, Long orderId) {
        PaymentModel payment = paymentService.getByOrderId(orderId);
        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다.");
        }
        return PaymentInfo.from(payment);
    }
}
