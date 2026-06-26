package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentV1Dto {

    /** 결제 요청: 어떤 주문(orderId)을 어떤 카드로 결제할지. 금액은 주문에서 가져온다. */
    public record PayRequest(
        Long orderId,
        CardType cardType,
        String cardNo
    ) {}

    public record PayResponse(
        Long orderId,
        String transactionKey,
        PaymentStatus status
    ) {
        public static PayResponse from(PaymentInfo info) {
            return new PayResponse(info.orderId(), info.transactionKey(), info.status());
        }
    }

    /** PG 콜백 본문. 우리가 쓰지 않는 필드(cardType/cardNo/amount/orderId 등)는 역직렬화에서 무시된다. */
    public record CallbackRequest(
        String transactionKey,
        PaymentStatus status,
        String reason
    ) {}
}
