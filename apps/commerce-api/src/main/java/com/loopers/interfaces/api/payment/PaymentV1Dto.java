package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public final class PaymentV1Dto {

    private PaymentV1Dto() {}

    public record RequestPayment(
        Long orderId,
        String cardType,
        String cardNo
    ) {}

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        Long userId,
        Long amount,
        CardType cardType,
        PaymentStatus status,
        String transactionKey,
        String failReason
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.userId(),
                info.amount(),
                info.cardType(),
                info.status(),
                info.transactionKey(),
                info.failReason()
            );
        }
    }

    /**
     * PG 시뮬레이터가 전송하는 콜백 페이로드. transactionKey + status 가 핵심.
     */
    public record Callback(
        String transactionKey,
        String orderId,
        String status,
        String reason
    ) {}
}
