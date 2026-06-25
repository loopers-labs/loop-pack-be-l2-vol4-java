package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentInitiator paymentInitiator;
    private final PaymentRequester paymentRequester;

    public PaymentInfo pay(Long userId, PaymentCommand.Pay command) {
        PaymentInitiator.Initiated initiated = paymentInitiator.initiate(userId, command.orderId());
        return paymentRequester.requestAndAssign(userId, command, initiated);
    }
}
