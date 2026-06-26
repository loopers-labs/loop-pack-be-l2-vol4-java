package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.PaymentModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PaymentDto {

    public record CreateRequest(
        @NotNull Long orderId,
        @NotBlank String cardType,
        @NotBlank String cardNo,
        @NotNull @Positive Long amount
    ) {}

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String cardType,
        String status,
        String transactionKey
    ) {
        public static PaymentResponse from(PaymentModel payment) {
            return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCardType().name(),
                payment.getStatus().name(),
                payment.getTransactionKey()
            );
        }
    }
}
