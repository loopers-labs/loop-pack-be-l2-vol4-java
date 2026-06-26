package com.loopers.infrastructure.payment.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "pgClient",
    url = "${pg.base-url}")
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgV1Dto.PaymentResponse requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgV1Dto.PaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgV1Dto.PaymentResponse getTransaction(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgV1Dto.OrderResponse findByOrderId(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
