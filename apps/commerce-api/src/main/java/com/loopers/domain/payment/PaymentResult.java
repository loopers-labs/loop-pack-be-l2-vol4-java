package com.loopers.domain.payment;

public record PaymentResult(
    Status status,
    String pgTransactionId,
    String failureReason
) {
    public enum Status {
        SUCCESS,
        FAILED,
        TIMEOUT
    }

    public static PaymentResult success(String pgTransactionId) {
        return new PaymentResult(Status.SUCCESS, pgTransactionId, null);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(Status.FAILED, null, reason);
    }

    public static PaymentResult timeout() {
        return new PaymentResult(Status.TIMEOUT, null, "외부 결제 시스템 응답 시간 초과");
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
