package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentV1Dto {

    public record PaymentRequest(Long orderId, String cardType, String cardNo) {
        public PaymentCommand.Pay toCommand() {
            return new PaymentCommand.Pay(orderId, cardType, cardNo);
        }
    }

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        PaymentStatus status,
        String transactionKey
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.status(),
                info.transactionKey()
            );
        }
    }
}
