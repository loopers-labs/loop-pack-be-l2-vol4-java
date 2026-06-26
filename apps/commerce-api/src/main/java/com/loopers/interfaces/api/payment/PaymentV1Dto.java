package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.enums.CardType;
import com.loopers.domain.payment.enums.PgTransactionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class PaymentV1Dto {

    public record CreateRequest(
            @NotBlank(message = "주문 번호는 필수입니다.") String orderNumber,
            @NotNull(message = "카드 종류는 필수입니다.") CardType cardType,
            @NotBlank(message = "카드 번호는 필수입니다.")
            @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
            String cardNo
    ) {}

    public record CallbackRequest(
            String transactionKey,
            String orderId,
            PgTransactionStatus status,
            String reason
    ) {}

    public record PaymentResponse(
            Long id,
            Long orderId,
            Long amount,
            String status,
            String transactionKey
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.id(), info.orderId(), info.amount(), info.status(), info.transactionKey());
        }
    }
}
