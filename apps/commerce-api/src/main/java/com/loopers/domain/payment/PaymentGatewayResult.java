package com.loopers.domain.payment;

public record PaymentGatewayResult(
    String transactionKey,
    PaymentGatewayStatus status,
    String reason
) {
    public static PaymentGatewayResult pending(String transactionKey, String reason) {
        return new PaymentGatewayResult(transactionKey, PaymentGatewayStatus.PENDING, reason);
    }

    public static PaymentGatewayResult success(String transactionKey, String reason) {
        return new PaymentGatewayResult(transactionKey, PaymentGatewayStatus.SUCCESS, reason);
    }

    public static PaymentGatewayResult failed(String transactionKey, String reason) {
        return new PaymentGatewayResult(transactionKey, PaymentGatewayStatus.FAILED, reason);
    }
}
