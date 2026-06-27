package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pg-simulator", url = "${pg-simulator.base-url}")
public interface PgSimulatorClient {

    @PostMapping("/api/v1/payments")
    PgSimulatorDto.ApiResponse<PgSimulatorDto.TransactionResponse> requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgSimulatorDto.PaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgSimulatorDto.ApiResponse<PgSimulatorDto.TransactionResponse> getTransaction(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgSimulatorDto.ApiResponse<PgSimulatorDto.OrderResponse> getTransactionsByOrder(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
