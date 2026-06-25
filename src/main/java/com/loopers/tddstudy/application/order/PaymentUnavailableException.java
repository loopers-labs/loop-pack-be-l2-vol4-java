package com.loopers.tddstudy.application.order;

public class PaymentUnavailableException extends RuntimeException {
    public PaymentUnavailableException(String message) {
        super(message);
    }
}
