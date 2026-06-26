package com.loopers.infrastructure.payment;

import com.loopers.interfaces.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * pg-simulator 호출용 Feign 선언. 타임아웃은 application.yml 의
 * spring.cloud.openfeign.client.config.pgClient 로 설정한다.
 */
@FeignClient(name = "pgClient", url = "${pg.base-url}")
public interface PgFeignClient {

    @PostMapping("/api/v1/payments")
    ApiResponse<PgV1Dto.TransactionResponse> requestPayment(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgV1Dto.PaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    ApiResponse<PgV1Dto.TransactionDetailResponse> getTransaction(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    ApiResponse<PgV1Dto.OrderResponse> findByOrderId(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
