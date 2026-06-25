package com.loopers.payment.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Toss PG(pg-simulator) 전송용 Feign 클라이언트. resilience(CB/Retry/RateLimiter)는 여기 붙이지 않고
 * 게이트웨이에서 수동 데코레이터로 조합한다 — feign.circuitbreaker 는 켜지 않는다.
 */
@FeignClient(name = "tossPgClient", url = "${payment.pg.toss.base-url}")
public interface TossPgClient {

    @PostMapping("/api/v1/payments")
    PgApiResponse<PgTransactionResponse> request(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PgPaymentRequest request);

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgApiResponse<PgTransactionDetail> getTransaction(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable("transactionKey") String transactionKey);

    @GetMapping("/api/v1/payments")
    PgApiResponse<PgOrderTransactions> getTransactionsByOrder(
            @RequestHeader("X-USER-ID") String userId,
            @RequestParam("orderId") String orderId);
}
