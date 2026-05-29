package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

public class PaymentV1Dto {

    public record ConfirmRequest(
        @NotNull UUID orderId,
        @NotBlank String pgTransactionId,
        @NotNull @Min(0) Long amount
    ) {}

    public record FailRequest(
        @NotNull UUID orderId,
        @NotBlank String pgTransactionId,
        @NotNull @Min(0) Long amount
    ) {}

    public record PaymentResponse(
        UUID id,
        UUID orderId,
        String pgTransactionId,
        PaymentStatus status,
        Long amount,
        ZonedDateTime createdAt
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.id(),
                info.orderId(),
                info.pgTransactionId(),
                info.status(),
                info.amount(),
                info.createdAt()
            );
        }
    }
}
