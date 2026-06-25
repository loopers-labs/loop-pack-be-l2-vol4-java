package com.loopers.infrastructure.payment;

import java.util.List;

public record TransactionsData(String orderId, List<TransactionData> transactions) {
}
