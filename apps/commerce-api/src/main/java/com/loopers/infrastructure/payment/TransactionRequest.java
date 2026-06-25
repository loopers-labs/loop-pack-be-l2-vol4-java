package com.loopers.infrastructure.payment;

public record TransactionRequest(String orderId, String cardType, String cardNo, long amount, String callbackUrl) {
}
