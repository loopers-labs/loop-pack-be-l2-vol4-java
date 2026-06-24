package com.loopers.interfaces.api.payment.dto;

import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PaymentV1Request(
    @NotNull @Positive Long orderId,
    @NotNull CardType cardType,
    @NotBlank @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
    String cardNo
) {

    /** cardNo(PAN)는 로그·예외에 평문 노출되지 않도록 끝 4자리만 남기고 마스킹한다. */
    @Override
    public String toString() {
        String maskedCardNo = cardNo == null || cardNo.length() < 4
            ? "****"
            : "****-****-****-" + cardNo.substring(cardNo.length() - 4);
        return "PaymentV1Request{orderId=" + orderId
            + ", cardType=" + cardType
            + ", cardNo=" + maskedCardNo + "}";
    }
}
