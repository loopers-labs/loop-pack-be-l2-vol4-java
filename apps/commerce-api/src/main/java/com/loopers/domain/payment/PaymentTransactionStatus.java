package com.loopers.domain.payment;

public record PaymentTransactionStatus(Outcome outcome, String transactionKey, PaymentStatus status, String reason) {

    public enum Outcome {
        FOUND,
        NOT_FOUND,
        UNKNOWN
    }

    public static PaymentTransactionStatus found(String transactionKey, PaymentStatus status, String reason) {
        return new PaymentTransactionStatus(Outcome.FOUND, transactionKey, status, reason);
    }

    public static PaymentTransactionStatus notFound() {
        return new PaymentTransactionStatus(Outcome.NOT_FOUND, null, null, null);
    }

    public static PaymentTransactionStatus unknown() {
        return new PaymentTransactionStatus(Outcome.UNKNOWN, null, null, null);
    }

    public boolean isFound() {
        return this.outcome == Outcome.FOUND;
    }

    public boolean isNotFound() {
        return this.outcome == Outcome.NOT_FOUND;
    }

    public boolean isStillProcessing() {
        return isFound() && this.status == PaymentStatus.PENDING;
    }
}
