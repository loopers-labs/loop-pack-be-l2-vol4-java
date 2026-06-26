package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgTransactionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PaymentV1Dto {

    public record PaymentRequest(
        @NotNull @Min(1) Long orderId,
        @NotNull CardType cardType,
        @NotNull @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.") String cardNo
    ) {
        public PaymentCommand toCommand() {
            return new PaymentCommand(orderId, cardType, cardNo);
        }
    }

    /** PG 가 결제 결과를 통보하는 콜백 본문 (PG TransactionInfo 구조). */
    public record CallbackRequest(
        @NotBlank String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        @NotNull PgTransactionStatus status,
        String reason
    ) {
    }

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String transactionKey,
        String status,
        Long amount,
        String reason
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.transactionKey(),
                info.status(),
                info.amount(),
                info.reason()
            );
        }
    }
}
