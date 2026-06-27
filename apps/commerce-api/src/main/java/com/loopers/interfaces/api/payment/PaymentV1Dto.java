package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentV1Dto {

    public record CreateRequest(
        @NotNull(message = "주문 식별자는 null일 수 없습니다.")
        Long orderId,

        @NotNull(message = "카드 종류는 null일 수 없습니다.")
        CardType cardType,

        @NotBlank(message = "카드 번호는 비어 있을 수 없습니다.")
        String cardNo
    ) {
    }

    public record CallbackRequest(
        @NotNull(message = "주문 식별자는 null일 수 없습니다.")
        Long orderId,

        @NotBlank(message = "거래 식별자는 비어 있을 수 없습니다.")
        String transactionKey,

        @NotNull(message = "결제 결과 상태는 null일 수 없습니다.")
        PaymentStatus status,

        String reason
    ) {
    }

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        int amount,
        String status,
        String transactionKey
    ) {

        public static PaymentResponse from(PaymentInfo paymentInfo) {
            return new PaymentResponse(
                paymentInfo.paymentId(),
                paymentInfo.orderId(),
                paymentInfo.amount(),
                paymentInfo.status().name(),
                paymentInfo.transactionKey()
            );
        }
    }
}
