package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

public record PaymentGatewayPaymentCommand(
    Long userId,
    Long orderId,
    CardType cardType,
    String cardNo,
    long amount
) {

    public PaymentGatewayPaymentCommand {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 사용자 ID는 비어있을 수 없습니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 주문 ID는 비어있을 수 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 비어있을 수 없습니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 비어있을 수 없습니다.");
        }
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
    }
}
