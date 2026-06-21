package com.loopers.application.payment.payment;

public record PaymentProcessResult(Status status, Long orderId) {
    public enum Status {
        SUCCESS,
        FAILED,
        CANCELED,
        EXPIRED,
        NOOP
    }

    public static PaymentProcessResult success(Long orderId) {
        return new PaymentProcessResult(Status.SUCCESS, orderId);
    }

    public static PaymentProcessResult failed(Long orderId) {
        return new PaymentProcessResult(Status.FAILED, orderId);
    }

    public static PaymentProcessResult canceled(Long orderId) {
        return new PaymentProcessResult(Status.CANCELED, orderId);
    }

    public static PaymentProcessResult expired(Long orderId) {
        return new PaymentProcessResult(Status.EXPIRED, orderId);
    }

    public static PaymentProcessResult noop(Long orderId) {
        return new PaymentProcessResult(Status.NOOP, orderId);
    }
}
