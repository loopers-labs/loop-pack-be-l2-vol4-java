package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "pgClient", url = "${pg.base-url}", primary = false)
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgApiResponse<PgPaymentResponse> requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgPaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgApiResponse<PgPaymentResponse> getTransaction(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );
}
