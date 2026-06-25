package com.loopers.application.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PG(pg-simulator) 호출 seam. Feign 인터페이스 자체가 seam 이라 테스트에서 fake/ mock 으로 대체 가능.
 * 회복 전략은 spring.cloud.openfeign.circuitbreaker(=resilience4j) + fallback 으로 적용된다.
 */
@FeignClient(name = "pgSimulator", url = "${pg.simulator.url}")
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgDto.Envelope<PgDto.TransactionResponse> requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgDto.PaymentRequest request);

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgDto.Envelope<PgDto.TransactionDetailResponse> getTransaction(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey);

    @GetMapping("/api/v1/payments")
    PgDto.Envelope<PgDto.OrderResponse> findByOrderId(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId);
}
