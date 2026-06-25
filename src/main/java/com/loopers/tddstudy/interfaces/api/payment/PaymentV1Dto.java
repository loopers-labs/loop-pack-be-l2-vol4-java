package com.loopers.tddstudy.interfaces.api.payment;

public class PaymentV1Dto {

    public record PaymentRequest(
            Long productId,    // orderId → productId 로 변경
            String cardType,
            String cardNo
    ) {}

    public record PaymentResponse(
            Long orderId,
            String status,
            String message
    ) {}
}
