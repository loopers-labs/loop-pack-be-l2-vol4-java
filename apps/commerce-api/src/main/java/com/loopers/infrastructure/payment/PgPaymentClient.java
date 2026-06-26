package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "pgPayment", url = "${pg-simulator.url}")
public interface PgPaymentClient {

    @PostMapping("/api/v1/payments")
    PgPaymentV1Dto.PgResponse<PgPaymentV1Dto.TransactionResponse> requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgPaymentV1Dto.PaymentRequest request
    );
}
