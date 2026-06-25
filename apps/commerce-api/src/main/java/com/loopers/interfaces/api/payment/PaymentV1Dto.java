package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record CreatePaymentRequest(
        @NotNull Long orderId,
        @NotNull CardType cardType,
        @NotBlank String cardNo
    ) {
        public PaymentCommand toCommand(Long userId) {
            return new PaymentCommand(orderId, userId, cardType, cardNo);
        }
    }

    public record PaymentResponse(
        Long id,
        Long orderId,
        CardType cardType,
        int amount,
        PaymentStatus status,
        String pgTransactionId,
        ZonedDateTime createdAt
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.id(),
                info.orderId(),
                info.cardType(),
                info.amount(),
                info.status(),
                info.pgTransactionId(),
                info.createdAt()
            );
        }
    }
}
