package com.loopers.domain.order;

public record PaymentResult(boolean success, String transactionId, String message) {

    public static PaymentResult success(String transactionId) {
        return new PaymentResult(true, transactionId, null);
    }

    public static PaymentResult failure(String message) {
        return new PaymentResult(false, null, message);
    }
}