package com.loopers.domain.payment;

public interface PgClient {
    PgTransactionResponse requestPayment(PgPaymentRequest request);
    PgTransactionResponse getTransaction(String transactionKey);
}
