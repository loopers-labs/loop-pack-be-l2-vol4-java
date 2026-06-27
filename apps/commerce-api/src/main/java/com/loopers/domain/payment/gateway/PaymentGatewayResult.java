package com.loopers.domain.payment.gateway;

public record PaymentGatewayResult(
    boolean success,
    boolean canceled,
    String transactionKey,
    String message,
    PaymentGatewayTransactionStatus transactionStatus,
    String orderId
) {
    public PaymentGatewayResult(boolean success, boolean canceled, String transactionKey, String message) {
        this(success, canceled, transactionKey, message, success ? PaymentGatewayTransactionStatus.SUCCESS : PaymentGatewayTransactionStatus.FAILED, null);
    }

    public static PaymentGatewayResult pending(String transactionKey) {
        return new PaymentGatewayResult(true, false, transactionKey, null, PaymentGatewayTransactionStatus.PENDING, null);
    }

    public static PaymentGatewayResult pending(String transactionKey, String orderId) {
        return new PaymentGatewayResult(true, false, transactionKey, null, PaymentGatewayTransactionStatus.PENDING, orderId);
    }

    public static PaymentGatewayResult success(String transactionKey, String orderId) {
        return new PaymentGatewayResult(true, false, transactionKey, null, PaymentGatewayTransactionStatus.SUCCESS, orderId);
    }

    public static PaymentGatewayResult failed(String transactionKey, String orderId, String message) {
        return new PaymentGatewayResult(false, false, transactionKey, message, PaymentGatewayTransactionStatus.FAILED, orderId);
    }

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
