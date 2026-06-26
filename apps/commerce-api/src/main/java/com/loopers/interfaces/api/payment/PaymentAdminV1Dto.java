package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.PaymentStatus;

public class PaymentAdminV1Dto {

    public record ReconcileResponse(
        Long orderId,
        String status
    ) {

        public static ReconcileResponse of(Long orderId, PaymentStatus status) {
            return new ReconcileResponse(orderId, status.name());
        }
    }
}
