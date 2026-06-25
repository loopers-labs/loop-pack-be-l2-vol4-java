package com.loopers.infrastructure.payment;

public record TransactionData(String transactionKey, String status, String reason) {
}
