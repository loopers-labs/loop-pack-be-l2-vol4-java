package com.loopers.infrastructure.payment.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "pgClient",
    url = "${pg.base-url}")
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgV1Dto.PaymentResponse requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgV1Dto.PaymentRequest request
    );
}
