package com.loopers.domain.payment;

import lombok.Getter;

@Getter
public class PaymentGatewayException extends RuntimeException {

    private final FailureReason failureReason;

    public PaymentGatewayException(FailureReason failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }
}
