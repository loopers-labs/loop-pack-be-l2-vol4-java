package com.loopers.payment.interfaces.api;

import com.loopers.payment.application.PaymentResult;

public class PaymentV1Response {

    public record Accepted(
            Long paymentId,
            String orderNumber,
            String status
    ) {
        public static Accepted from(PaymentResult.Accepted result) {
            return new Accepted(result.paymentId(), result.orderNumber(), result.status().name());
        }
    }
}
