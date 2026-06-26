package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgTransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public class PaymentV1Dto {

    public record PaymentRequest(
        @Schema(example = "1") String orderId,
        @Schema(example = "SAMSUNG") CardType cardType,
        @Schema(example = "1234-5678-9814-1451") String cardNo
    ) {}

    public record CallbackRequest(
        @Schema(example = "TX-001") String transactionKey,
        @Schema(example = "1") String orderId,
        @Schema(example = "SAMSUNG") CardType cardType,
        @Schema(example = "1234-5678-9814-1451") String cardNo,
        @Schema(example = "100000") Long amount,
        @Schema(example = "SUCCESS") PgTransactionStatus status,
        String reason
    ) {}

    public record Response(
        String paymentId,
        String transactionKey,
        String status,
        String reason
    ) {
        public static Response from(PaymentInfo info) {
            return new Response(
                info.paymentId(),
                info.transactionKey(),
                info.status().name(),
                info.failureReason()
            );
        }
    }
}
