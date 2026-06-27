package com.loopers.domain.payment.gateway;

public class PaymentGatewayCommand {
    public record Request(
        String userId,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}
}
