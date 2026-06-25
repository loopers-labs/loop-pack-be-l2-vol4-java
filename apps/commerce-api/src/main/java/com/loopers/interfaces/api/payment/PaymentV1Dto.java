package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgTransactionStatus;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        CardType cardType,
        String cardNo
    ) {}

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        CardType cardType,
        String cardNo,
        Long amount,
        PgTransactionStatus status,
        String reason
    ) {}

    public record Response(
        Long paymentId,
        String transactionKey,
        String status,
        String reason
    ) {
        public static Response from(PaymentInfo info) {
            return new Response(
                info.paymentId(),
                info.transactionKey(),
                info.status().name(),
                info.failureReason()
            );
        }
    }
}
