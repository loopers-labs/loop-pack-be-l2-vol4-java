package com.loopers.infrastructure.pg;

import java.util.Optional;

public interface PgPaymentClient {
    PgPaymentResult request(PgPaymentRequest request);
    Optional<PgTransactionResponse> getStatus(String transactionKey, Long userId);
    Optional<PgTransactionResponse> findByOrderId(String orderId, Long userId);
}
