package com.loopers.infrastructure.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pg-client", url = "${pg.client.url}")
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgResponse.TransactionResponse createTransaction(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgRequest.CreateTransaction request
    );

    @GetMapping("/api/v1/payments")
    PgResponse.OrderResponse getTransactionsByOrderId(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
