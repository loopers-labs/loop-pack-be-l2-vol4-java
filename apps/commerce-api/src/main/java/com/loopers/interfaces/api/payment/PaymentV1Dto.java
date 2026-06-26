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

    /**
     * pg-simulator 콜백 페이로드(TransactionInfo). ApiResponse 래퍼 없이 raw JSON으로 전달된다.
     * 주문 확정은 transactionKey로 찾은 우리 결제 레코드의 orderId를 신뢰하므로, 여기서는
     * transactionKey/status/reason만 사용한다(나머지 필드는 계약 호환을 위해 수신만).
     */
    public record CallbackRequest(
        String transactionKey,
        String orderId,
        CardType cardType,
        String cardNo,
        Long amount,
        PaymentStatus status,
        String reason
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
