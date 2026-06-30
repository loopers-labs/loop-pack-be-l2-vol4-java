package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class PaymentStatusSynchronizer {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    @Transactional
    public PaymentInfo syncPayment(String userLoginId, Long orderId) {
        Payment payment = paymentService.getPayment(orderId, userLoginId);
        PaymentGatewayResult result = paymentGateway.getByOrder(userLoginId, orderId);
        payment.applyGatewayResult(result);
        payment.failIfLookupEmptyGracePeriodElapsed(
            ZonedDateTime.now(clock),
            paymentProperties.lookupEmptyFailureDelay()
        );
        return PaymentInfo.from(paymentService.completeIfPending(payment));
    }
}
