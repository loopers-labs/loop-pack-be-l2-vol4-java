package com.loopers.domain.payment;

public record PaymentRequestResult(Outcome outcome, String transactionKey, String reason) {

    public enum Outcome {
        ACCEPTED,
        UNKNOWN,
        REJECTED
    }

    public static PaymentRequestResult accepted(String transactionKey) {
        return new PaymentRequestResult(Outcome.ACCEPTED, transactionKey, null);
    }

    public static PaymentRequestResult unknown() {
        return new PaymentRequestResult(Outcome.UNKNOWN, null, null);
    }

    public static PaymentRequestResult rejected(String reason) {
        return new PaymentRequestResult(Outcome.REJECTED, null, reason);
    }
}
