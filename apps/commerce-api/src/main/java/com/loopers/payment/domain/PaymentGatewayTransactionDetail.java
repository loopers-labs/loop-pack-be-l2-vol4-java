package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

public record PaymentGatewayTransactionDetail(
    String transactionKey,
    Long orderId,
    CardType cardType,
    long amount,
    PgPaymentStatus status,
    String reason
) {

    public PaymentGatewayTransactionDetail {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 키는 비어있을 수 없습니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 주문 ID는 비어있을 수 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 비어있을 수 없습니다.");
        }
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 거래 상태는 비어있을 수 없습니다.");
        }
    }
}
