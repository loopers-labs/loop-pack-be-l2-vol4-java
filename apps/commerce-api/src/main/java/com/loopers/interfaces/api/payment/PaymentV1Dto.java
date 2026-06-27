package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        CardType cardType,
        String cardNo
    ) {
        public PaymentCommand.Request toCommand(Long userId) {
            return new PaymentCommand.Request(userId, orderId, cardType, cardNo);
        }
    }

    public record PaymentResponse(
        String transactionKey
    ) {}

    public record CallbackRequest(
        String transactionKey,
        String status,
        String reason
    ) {
        public PaymentCommand.Callback toCommand() {
            return new PaymentCommand.Callback(transactionKey, PaymentStatus.valueOf(status), reason);
        }
    }
}