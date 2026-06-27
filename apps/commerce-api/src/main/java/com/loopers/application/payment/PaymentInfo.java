package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(Long orderId, String transactionKey, PaymentStatus status) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(payment.getOrderId(), payment.getTransactionKey(), payment.getStatus());
    }
}
