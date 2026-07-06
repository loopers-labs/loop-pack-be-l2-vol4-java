package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PaymentV1Dto {

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
        @NotBlank(message = "transactionKey는 필수입니다.")
        String transactionKey,

        @NotBlank(message = "orderId는 필수입니다.")
        @Pattern(regexp = "^\\d+$", message = "orderId는 숫자 문자열이어야 합니다.")
        String orderId,

        String cardType,
        String cardNo,
        Long amount,

        @NotBlank(message = "status는 필수입니다.")
        String status,

        String reason
    ) {}
}
