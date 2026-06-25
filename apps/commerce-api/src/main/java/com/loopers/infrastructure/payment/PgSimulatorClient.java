package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "pg-simulator", url = "${pg-simulator.base-url}")
public interface PgSimulatorClient {

    @PostMapping("/api/v1/payments")
    PgSimulatorDto.ApiResponse<PgSimulatorDto.TransactionResponse> requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgSimulatorDto.PaymentRequest request
    );
}
