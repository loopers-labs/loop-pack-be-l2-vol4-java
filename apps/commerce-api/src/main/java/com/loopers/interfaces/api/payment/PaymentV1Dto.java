package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentV1Dto {

    /**
     * 결제 시작 요청. cardNo는 pg-simulator 계약상 "xxxx-xxxx-xxxx-xxxx" 형식이어야 한다.
     * 검증은 어댑터/도메인에서 수행하며, 여기서는 원문 그대로 전달한다.
     */
    public record PayRequest(
        Long orderId,
        CardType cardType,
        String cardNo
    ) {}

    public record PayResponse(
        Long id,
        Long orderId,
        String transactionKey,
        PaymentStatus status,
        String reason
    ) {
        public static PayResponse from(PaymentInfo info) {
            return new PayResponse(
                info.id(),
                info.orderId(),
                info.transactionKey(),
                info.status(),
                info.reason()
            );
        }
    }
}
