package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "payment-gateway", url = "${payment-gateway.url}", configuration = PaymentGatewayFeignConfig.class)
public interface PaymentGatewayFeignClient {

    @PostMapping("/api/v1/payments")
    TransactionResponse requestPayment(@RequestHeader("X-USER-ID") String userNumber, @RequestBody TransactionRequest request);

    @GetMapping("/api/v1/payments")
    TransactionsResponse findTransactionsByOrderId(@RequestHeader("X-USER-ID") String userNumber, @RequestParam("orderId") String orderId);

    record TransactionRequest(String orderId, String cardType, String cardNo, long amount, String callbackUrl) {
    }

    record TransactionResponse(TransactionData data) {
    }

    record TransactionData(String transactionKey, String status, String reason) {
    }

    record TransactionsResponse(TransactionsData data) {
    }

    record TransactionsData(String orderId, List<TransactionData> transactions) {
    }

}
