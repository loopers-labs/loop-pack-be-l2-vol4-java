package com.loopers.payment.infrastructure.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "pg-simulator",
    url = "${pg.url}",
    fallbackFactory = PgPaymentFallbackFactory.class,
    configuration = PgFeignConfig.class
)
public interface PgPaymentClient {

    @PostMapping("/api/v1/payments")
    PgPaymentClientDto.TransactionResponse requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgPaymentClientDto.PaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgPaymentClientDto.TransactionResponse getTransaction(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgPaymentClientDto.OrderTransactionsResponse getTransactionsByOrder(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
