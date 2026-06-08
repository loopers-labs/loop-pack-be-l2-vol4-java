package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;

public record PaymentInfo(
        Long id,
        Long orderId,
        Long amount,
        String status
) {
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount().getValue(),
                payment.getStatus().getDescription()
        );
    }
}
