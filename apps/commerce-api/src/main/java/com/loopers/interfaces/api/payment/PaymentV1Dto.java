package com.loopers.interfaces.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class PaymentV1Dto {

    public record PayRequest(
        @NotNull UUID orderId,
        @NotBlank String cardType,
        @NotBlank String cardNo
    ) {}

    public record PayResponse(String transactionKey) {}

    public record CallbackPayload(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}
}
