package com.loopers.domain.pg;

public interface PgGateway {
    PgTransactionResult request(String userId, String orderId, String cardType, String cardNo, Long amount, String callbackUrl);
}
