package com.loopers.infrastructure.pg;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@CircuitBreaker(name = "pg-payment")
@Retry(name = "pg-payment")
@RequiredArgsConstructor
@Component
public class PgClientWrapper {

    private final PgClient pgClient;

    public PgResponse.TransactionResponse createTransaction(String userId, PgRequest.CreateTransaction request) {
        return pgClient.createTransaction(userId, request);
    }

    public PgResponse.OrderResponse getTransactionsByOrderId(String userId, String pgOrderId) {
        return pgClient.getTransactionsByOrderId(userId, pgOrderId);
    }
}
