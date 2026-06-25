package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

/** 결제 접수(ACK) 정보. 결과(영수증)가 아니라 접수증 — 최종 결과는 콜백/폴링으로 확정된다. */
public record PaymentInfo(
    Long orderId,
    String transactionKey,
    PaymentStatus status,
    Long amount,
    String reason
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
            payment.getOrderId(),
            payment.getTransactionKey(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getReason()
        );
    }
}
