package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.PaymentStatus;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        CardType cardType,
        String cardNo
    ) {
    }

    public record PaymentResponse(
        Long paymentId,
        PaymentStatus status
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.paymentId(), info.status());
        }
    }
}
