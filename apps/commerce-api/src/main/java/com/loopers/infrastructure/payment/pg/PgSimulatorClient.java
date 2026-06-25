package com.loopers.infrastructure.payment.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pg-simulator", url = "${pg.base-url}")
public interface PgSimulatorClient {

    @PostMapping("/api/v1/payments")
    PgPaymentDto.TransactionResponse request(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgPaymentDto.PaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgPaymentDto.TransactionResponse getByTransactionKey(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgPaymentDto.OrderTransactionResponse getByOrderId(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
