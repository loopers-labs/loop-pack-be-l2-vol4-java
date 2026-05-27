package com.loopers.domain.payment.gateway;

public record PaymentGatewayResult(
    boolean success,
    boolean canceled,
    String transactionKey,
    String message
) {
    public static PaymentGatewayResult success(String transactionKey) {
        return new PaymentGatewayResult(true, false, transactionKey, null);
    }

    public static PaymentGatewayResult failed(String message) {
        return new PaymentGatewayResult(false, false, null, message);
    }

    public static PaymentGatewayResult canceled(String message) {
        return new PaymentGatewayResult(false, true, null, message);
    }
}
