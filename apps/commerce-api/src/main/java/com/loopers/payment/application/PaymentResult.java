package com.loopers.payment.application;

import com.loopers.payment.domain.PaymentStatus;

public class PaymentResult {

    public record Pending(Long paymentId, String orderNumber, long amount) {
    }

    public record Accepted(Long paymentId, String orderNumber, PaymentStatus status) {
    }
}
