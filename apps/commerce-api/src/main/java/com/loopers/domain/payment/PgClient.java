package com.loopers.domain.payment;

public interface PgClient {
    PgTransactionResponse requestPayment(PgPaymentRequest request, String userId);
    PgTransactionResponse getTransaction(String transactionKey, String userId);
}
