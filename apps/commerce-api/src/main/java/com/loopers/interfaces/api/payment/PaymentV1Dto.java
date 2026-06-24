package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PaymentV1Dto {

    public enum CardTypeDto {
        SAMSUNG, KB, HYUNDAI;

        public CardType toDomain() {
            return CardType.valueOf(this.name());
        }
    }

    public record PaymentRequest(
            @NotNull Long orderId,
            @NotNull CardTypeDto cardType,
            @NotBlank @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$") String cardNo
    ) {
    }

    public record PaymentResponse(
            String transactionKey,
            String status
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.transactionKey(), info.status());
        }
    }
}
