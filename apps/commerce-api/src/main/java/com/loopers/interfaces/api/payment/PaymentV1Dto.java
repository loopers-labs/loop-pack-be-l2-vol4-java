package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import jakarta.validation.constraints.NotNull;

public class PaymentV1Dto {

    public record CreateRequest(
            @NotNull(message = "주문 ID는 필수입니다.") Long orderId
    ) {}

    public record PaymentResponse(
            Long id,
            Long orderId,
            Long amount,
            String status
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.id(), info.orderId(), info.amount(), info.status());
        }
    }
}
