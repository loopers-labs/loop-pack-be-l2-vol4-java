package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * pg-simulator 호출용 선언적 HTTP 클라이언트.
 * - name "pg" : application.yml 의 feign client config / resilience4j 인스턴스 이름과 매칭
 * - url      : pg.base-url
 * - configuration : X-USER-ID 헤더 주입 + 5xx/4xx 예외 분리 (PgFeignConfig)
 * 타임아웃(connect/read)은 application.yml 의 spring.cloud.openfeign.client.config.pg 에서 적용된다.
 */
@FeignClient(name = "pg", url = "${pg.base-url}", configuration = PgFeignConfig.class)
public interface PgFeignClient {

    @PostMapping("/api/v1/payments")
    PgClientDto.ApiResponse<PgClientDto.TransactionResponse> request(
            @RequestBody PgClientDto.PaymentRequest request);

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgClientDto.ApiResponse<PgClientDto.TransactionDetailResponse> getByTransactionKey(
            @PathVariable("transactionKey") String transactionKey);

    @GetMapping("/api/v1/payments")
    PgClientDto.ApiResponse<PgClientDto.OrderResponse> getByOrderId(
            @RequestParam("orderId") String orderId);
}
