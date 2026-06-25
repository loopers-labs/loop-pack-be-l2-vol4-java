package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PaymentV1Dto {

    /**
     * cardNo 패턴은 PG-Simulator validate(`xxxx-xxxx-xxxx-xxxx`) 와 동일하게 적용해
     * PG 도달 전에 친화적인 400 응답을 돌려준다.
     */
    public record PaymentRequest(
        @NotNull Long orderId,
        @NotNull CardType cardType,
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$",
            message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
        String cardNo
    ) {
        public PaymentCommand toCommand() {
            return new PaymentCommand(orderId, cardType, cardNo);
        }
    }

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String status,        // PROCESSING / SUCCESS / FAILED / FAILED_RETRYABLE
        String pollingUrl,
        String message
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.userStatus(),
                info.pollingUrl(),
                info.message()
            );
        }
    }

    private PaymentV1Dto() {
    }
}
