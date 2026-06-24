package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-gateway", url = "${payment-gateway.url}", configuration = PaymentGatewayFeignConfig.class)
public interface PaymentGatewayFeignClient {

    @PostMapping("/api/v1/payments")
    Response requestPayment(@RequestHeader("X-USER-ID") String userNumber, @RequestBody Request request);

    record Request(String orderId, String cardType, String cardNo, long amount, String callbackUrl) {
    }

    record Response(TransactionData data) {
    }

    record TransactionData(String transactionKey, String status, String reason) {
    }
}
