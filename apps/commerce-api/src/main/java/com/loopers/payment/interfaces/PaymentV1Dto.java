package com.loopers.payment.interfaces;

import com.loopers.payment.application.PaymentInfo;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        String cardType,
        String cardNo
    ) {}

    public record PaymentResponse(
        String transactionKey,
        String status,
        Long amount
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.transactionKey(),
                info.status().name(),
                info.amount()
            );
        }
    }

    public record CallbackRequest(
        String transactionKey,
        Long orderId
    ) {}
}
