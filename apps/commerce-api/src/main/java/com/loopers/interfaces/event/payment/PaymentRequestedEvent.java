package com.loopers.interfaces.event.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;

public record PaymentRequestedEvent(
    Long paymentId,
    Long orderId,
    Long memberId,
    CardType cardType,
    String cardNo,
    Long amount
) {
    public static PaymentRequestedEvent of(PaymentModel payment, Long memberId) {
        return new PaymentRequestedEvent(
            payment.getId(),
            payment.getOrderId(),
            memberId,
            payment.getCardType(),
            payment.getCardNo(),
            payment.getAmount()
        );
    }
}
