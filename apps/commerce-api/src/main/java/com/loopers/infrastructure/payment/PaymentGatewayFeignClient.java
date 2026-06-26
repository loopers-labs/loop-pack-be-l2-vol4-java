package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "pgSimulator",
        url = "${payment-gateway.url}",
        configuration = PaymentGatewayFeignConfig.class,
        primary = false   // 테스트에서 @Primary 스텁으로 교체 가능하도록(운영은 단일 빈이라 무영향)
)
public interface PaymentGatewayFeignClient {

    @PostMapping("/api/v1/payments")
    PgApiResponse<PgTransactionResponse> request(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PgPaymentRequest body);

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgApiResponse<PgTransactionDetailResponse> getByKey(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable("transactionKey") String transactionKey);

    @GetMapping("/api/v1/payments")
    PgApiResponse<PgOrderResponse> getByOrderId(
            @RequestHeader("X-USER-ID") String userId,
            @RequestParam("orderId") String orderId);
}
