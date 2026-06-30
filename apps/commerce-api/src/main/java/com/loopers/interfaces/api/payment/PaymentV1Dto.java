package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;

public class PaymentV1Dto {

    public record PaymentRequest(Long orderId, CardType cardType, String cardNo) {}

    /** PG 콜백 본문 (pg-simulator 의 TransactionInfo). transactionKey/status/reason 만 사용. */
    public record PgCallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}

    public record PaymentResponse(
        Long orderId,
        String transactionKey,
        String status,
        Long amount,
        String reason
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.orderId(),
                info.transactionKey(),
                info.status().name(),
                info.amount(),
                info.reason()
            );
        }
    }
}
