package com.loopers.interfaces.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentV1Dto {

    /**
     * 결제 승인 요청 — PG 결제창 인증 완료 후 successUrl 에서 프론트가 전달받은 값.
     *
     * <p>amount 는 브라우저를 거쳐 온 값이므로 서버가 DB 주문 금액과 대조해 위변조를 검증한다.
     */
    public record ConfirmRequest(
        @NotBlank(message = "paymentKey는 필수입니다.")
        String paymentKey,

        @NotNull(message = "orderId는 필수입니다.")
        Long orderId,

        @NotNull(message = "amount는 필수입니다.")
        Long amount
    ) {}
}
