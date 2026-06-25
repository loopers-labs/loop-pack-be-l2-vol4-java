package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;

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
