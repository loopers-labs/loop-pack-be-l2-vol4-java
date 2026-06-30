package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentCardType;
import com.loopers.domain.payment.PaymentPendingReason;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
    Long id,
    String userLoginId,
    Long orderId,
    PaymentCardType cardType,
    Long amount,
    PaymentStatus status,
    PaymentPendingReason pendingReason,
    String transactionKey,
    String reason
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getUserLoginId(),
            payment.getOrderId(),
            payment.getCardType(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getPendingReason(),
            payment.getTransactionKey(),
            payment.getReason()
        );
    }
}
