package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.application.payment.PaymentCriteria;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public final class PaymentV1Dto {

    private PaymentV1Dto() {
    }

    public record PayRequest(Long orderId, CardType cardType, String cardNo) {

        public PaymentCriteria.Pay toCriteria(Long userId) {
            return new PaymentCriteria.Pay(userId, orderId, cardType, cardNo);
        }
    }

    /**
     * PG 콜백 바디(pg-simulator 의 TransactionInfo). 결과 확정에 필요한 키·상태·사유만 사용한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackRequest(String transactionKey, PaymentStatus status, Long amount, String reason) {

        public PaymentCriteria.Callback toCriteria() {
            return new PaymentCriteria.Callback(transactionKey, status, amount, reason);
        }
    }

    public record PayResponse(
            Long id,
            Long orderId,
            String transactionKey,
            CardType cardType,
            String maskedCardNo,
            long amount,
            PaymentStatus status,
            String reason
    ) {

        public static PayResponse from(PaymentInfo.Requested info) {
            return new PayResponse(
                    info.id(),
                    info.orderId(),
                    info.transactionKey(),
                    info.cardType(),
                    info.maskedCardNo(),
                    info.amount(),
                    info.status(),
                    info.reason()
            );
        }
    }

    public record DetailResponse(
            Long id,
            Long orderId,
            String transactionKey,
            CardType cardType,
            String maskedCardNo,
            long amount,
            PaymentStatus status,
            String reason
    ) {

        public static DetailResponse from(PaymentInfo.Detail info) {
            return new DetailResponse(
                    info.id(),
                    info.orderId(),
                    info.transactionKey(),
                    info.cardType(),
                    info.maskedCardNo(),
                    info.amount(),
                    info.status(),
                    info.reason()
            );
        }
    }
}
