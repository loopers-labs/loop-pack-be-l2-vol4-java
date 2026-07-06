package com.loopers.payment.domain;

public record PaymentGatewayResult(String transactionKey, PgProvider pgProvider, PaymentStatus status, String reason) {
}
