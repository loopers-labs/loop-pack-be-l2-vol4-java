package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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

    /** pg-simulator 비동기 결제 요청. */
    public record PaymentRequest(
        @NotNull(message = "orderId는 필수입니다.")
        Long orderId,

        @NotNull(message = "cardType은 필수입니다.")
        CardType cardType,

        @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
        @NotBlank(message = "cardNo는 필수입니다.")
        String cardNo,

        @NotNull(message = "amount는 필수입니다.")
        Long amount
    ) {}

    /** pg-simulator 결제 요청 응답 — transactionKey 와 PENDING 상태를 반환한다. */
    public record PaymentResponse(String transactionKey, String status) {
        public static PaymentResponse pending(String transactionKey) {
            return new PaymentResponse(transactionKey, "PENDING");
        }
    }

    /**
     * pg-simulator 가 비동기로 보내는 콜백 페이로드.
     *
     * <p>pg-simulator 의 {@code TransactionInfo} 와 동일한 구조.
     * orderId 는 pg-simulator 에서 String 으로 전달된다.
     */
    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
    ) {}
}
