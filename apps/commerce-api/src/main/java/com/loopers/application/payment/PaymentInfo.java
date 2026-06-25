package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

public final class PaymentInfo {

    private PaymentInfo() {
    }

    /**
     * 결제 요청 결과. 비동기 PG 특성상 요청 직후엔 보통 {@link PaymentStatus#PENDING} 이며,
     * 확정(SUCCESS/FAILED)은 이후 콜백/정산으로 갱신된다.
     */
    public record Requested(
            Long id,
            Long orderId,
            String transactionKey,
            CardType cardType,
            String maskedCardNo,
            long amount,
            PaymentStatus status,
            String reason
    ) {

        public static Requested from(Payment payment) {
            return new Requested(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getTransactionKey(),
                    payment.getCardType(),
                    payment.getMaskedCardNo(),
                    payment.getAmount().getAmount(),
                    payment.getStatus(),
                    payment.getReason()
            );
        }
    }

    /**
     * 결제 상태 조회 결과. 우리 DB 기준의 현재 상태를 그대로 노출한다(PG 를 재호출하지 않음).
     * PENDING 이면 "처리중" 으로 정직하게 보이며, 확정은 콜백/정산으로 갱신된다.
     */
    public record Detail(
            Long id,
            Long orderId,
            String transactionKey,
            CardType cardType,
            String maskedCardNo,
            long amount,
            PaymentStatus status,
            String reason
    ) {

        public static Detail from(Payment payment) {
            return new Detail(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getTransactionKey(),
                    payment.getCardType(),
                    payment.getMaskedCardNo(),
                    payment.getAmount().getAmount(),
                    payment.getStatus(),
                    payment.getReason()
            );
        }
    }
}
