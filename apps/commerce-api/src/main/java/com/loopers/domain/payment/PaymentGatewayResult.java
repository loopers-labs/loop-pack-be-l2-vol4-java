package com.loopers.domain.payment;

public record PaymentGatewayResult(
    String transactionKey,
    PaymentGatewayStatus status,
    PaymentPendingReason pendingReason,
    String reason
) {
    public static PaymentGatewayResult pending(String transactionKey, String reason) {
        return pending(transactionKey, PaymentPendingReason.WAITING_CALLBACK, reason);
    }

    public static PaymentGatewayResult pending(String transactionKey, PaymentPendingReason pendingReason, String reason) {
        return new PaymentGatewayResult(transactionKey, PaymentGatewayStatus.PENDING, pendingReason, reason);
    }

    public static PaymentGatewayResult success(String transactionKey, String reason) {
        return new PaymentGatewayResult(transactionKey, PaymentGatewayStatus.SUCCESS, null, reason);
    }

    public static PaymentGatewayResult failed(String transactionKey, String reason) {
        return new PaymentGatewayResult(transactionKey, PaymentGatewayStatus.FAILED, null, reason);
    }
}
