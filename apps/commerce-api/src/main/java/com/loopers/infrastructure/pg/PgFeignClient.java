package com.loopers.infrastructure.pg;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "pg-client", url = "${pg.base-url}", configuration = PgFeignClientConfig.class)
public interface PgFeignClient {

    @Retry(name = "pg-payment")
    @CircuitBreaker(name = "pg-payment")
    @PostMapping("/api/v1/payments")
    PgApiResponse.Payment requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgPaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgApiResponse.PaymentStatus getPaymentStatus(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );
}
