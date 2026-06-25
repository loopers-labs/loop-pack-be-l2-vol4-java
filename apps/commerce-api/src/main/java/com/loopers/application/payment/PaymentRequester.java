package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequester {

    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;

    @CircuitBreaker(name = "pg", fallbackMethod = "stayPending")
    public PaymentInfo requestAndAssign(Long userId, PaymentCommand.Pay command, PaymentInitiator.Initiated initiated) {
        PaymentGatewayResult ack = paymentGateway.requestPayment(new PaymentGatewayCommand(
            userId, command.orderId(), command.cardType(), command.cardNo(), initiated.amount()));
        PaymentModel payment = paymentService.assignTransactionKey(initiated.paymentId(), ack.transactionKey());
        return PaymentInfo.from(payment);
    }

    private PaymentInfo stayPending(Long userId, PaymentCommand.Pay command, PaymentInitiator.Initiated initiated, Throwable cause) {
        log.warn("PG 접수 실패/서킷 차단 → 결제를 진행중(PENDING)으로 강등합니다. [paymentId={}, orderId={}, cause={}]",
            initiated.paymentId(), command.orderId(), cause.toString());
        return new PaymentInfo(initiated.paymentId(), command.orderId(), PaymentStatus.PENDING, null);
    }
}
