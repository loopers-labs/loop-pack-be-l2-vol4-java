package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PG 시뮬레이터 REST 클라이언트 — Spring Cloud OpenFeign.
 * <p>
 * Feign 자체 timeout 은 PgFeignConfig 에서 설정한다.
 * Resilience4j(@CircuitBreaker / @TimeLimiter / @Retry) 는 본 클라이언트를 감싸는
 * PgClientAdapter 가 담당한다 — Feign 단에서 막으면 CB 동작 분석이 어렵기 때문.
 */
@FeignClient(
    name = "pg-simulator",
    url = "${pg.simulator.base-url}",
    configuration = PgFeignConfig.class
)
public interface PgFeignClient {

    @PostMapping(path = "/api/v1/payments")
    PgFeignDto.PgResponse<PgFeignDto.RequestResponse> request(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgFeignDto.RequestPayload payload
    );

    @GetMapping(path = "/api/v1/payments/{transactionKey}")
    PgFeignDto.PgResponse<PgFeignDto.TransactionView> getByTransactionKey(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping(path = "/api/v1/payments")
    PgFeignDto.PgResponse<PgFeignDto.TransactionListView> findByOrderId(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
