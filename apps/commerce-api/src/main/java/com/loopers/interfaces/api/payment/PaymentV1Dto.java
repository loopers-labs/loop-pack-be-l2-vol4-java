package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;

public class PaymentV1Dto {

    public record PaymentRequest(Long orderId, CardType cardType, String cardNo) {}

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String transactionKey,
        String status,
        Long amount
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.transactionKey(),
                info.status().name(),
                info.amount()
            );
        }
    }

    /** PG가 비동기 결제 결과를 통지하는 콜백 본문 (PG TransactionInfo). */
    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}
}
