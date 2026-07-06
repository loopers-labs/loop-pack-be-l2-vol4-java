package com.loopers.payment.interfaces.api;

import com.loopers.payment.application.PaymentCommand;
import com.loopers.payment.domain.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentV1Request {

    /**
     * 결제 요청. {@code orderId} 는 외부 계약상 필드명이며 값은 우리 주문번호(orderNumber)다.
     */
    public record Pay(
            @NotBlank(message = "orderId 는 필수입니다.")
            String orderId,

            @NotNull(message = "cardType 은 필수입니다.")
            CardType cardType,

            @NotBlank(message = "cardNo 는 필수입니다.")
            String cardNo
    ) {
        public PaymentCommand.Pay toCommand(Long userId) {
            return new PaymentCommand.Pay(userId, orderId, cardType, cardNo);
        }
    }
}
