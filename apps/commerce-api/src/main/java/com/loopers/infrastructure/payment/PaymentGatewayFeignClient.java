package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-gateway", url = "${payment-gateway.url}", configuration = PaymentGatewayFeignConfig.class)
public interface PaymentGatewayFeignClient {

    @PostMapping("/api/v1/payments")
    TransactionResponse requestPayment(@RequestHeader("X-USER-ID") String userNumber, @RequestBody TransactionRequest request);

    @GetMapping("/api/v1/payments")
    TransactionsResponse findTransactionsByOrderId(@RequestHeader("X-USER-ID") String userNumber, @RequestParam("orderId") String orderId);
}
